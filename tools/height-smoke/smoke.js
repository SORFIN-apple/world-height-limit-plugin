#!/usr/bin/env node
'use strict'

// Run only against an isolated local test server. Paper and the world are kept
// under the ignored work/ directory; no installed Minecraft profile is used.
const fs = require('node:fs')
const fsp = require('node:fs/promises')
const path = require('node:path')
const os = require('node:os')
const net = require('node:net')
const crypto = require('node:crypto')
const { spawn } = require('node:child_process')
const { Readable } = require('node:stream')
const { pipeline } = require('node:stream/promises')
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const repo = path.resolve(__dirname, '../..')
const mcVersion = process.env.MC_VERSION || '1.21.11'
const botVersion = process.env.BOT_VERSION || mcVersion
const java = process.env.JAVA_BIN || 'java'
const pluginJar = path.resolve(process.env.PLUGIN_JAR || path.join(repo, 'build/libs/WorldHeightLimit-v1.2.0-Paper-Purpur-Folia-1.21-26.3.jar'))
const observerJar = process.env.OBSERVER_JAR ? path.resolve(process.env.OBSERVER_JAR) : null
const vanillaServerJar = process.env.VANILLA_SERVER_JAR ? path.resolve(process.env.VANILLA_SERVER_JAR) : null
const engineProbeOnly = process.env.ENGINE_PROBE_ONLY === '1'
const smokeRoot = path.resolve(process.env.SMOKE_ROOT || path.join(repo, 'work/height-smoke'))
const versionRoot = path.join(smokeRoot, mcVersion)
const lab = path.join(versionRoot, new Date().toISOString().replace(/[:.]/g, '-'))
const serverJar = path.join(lab, 'paper.jar')
const cachedServerJar = path.join(versionRoot, 'paper.jar')
const reportPath = path.join(lab, 'report.json')
const timeoutMs = 180_000
const report = { minecraft: mcVersion, botVersion, pluginJar: engineProbeOnly ? null : pluginJar, java, checks: {}, status: 'BLOCKED' }

function delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)) }
function assert(condition, message) { if (!condition) throw new Error(message) }

async function freePort() {
  const server = net.createServer()
  await new Promise((resolve, reject) => server.once('error', reject).listen(0, '127.0.0.1', resolve))
  const port = server.address().port
  await new Promise(resolve => server.close(resolve))
  return port
}

async function downloadPaper() {
  const endpoint = `https://fill.papermc.io/v3/projects/paper/versions/${encodeURIComponent(mcVersion)}/builds`
  const headers = { 'User-Agent': 'world-height-limit-smoke/0.1 (local plugin test)' }
  const response = await fetch(endpoint, { headers })
  assert(response.ok, `Paper build lookup failed: HTTP ${response.status}`)
  const builds = await response.json()
  const pinnedBuild = process.env.PAPER_BUILD
  const build = pinnedBuild
    ? builds.find(item => String(item.id) === pinnedBuild && item.downloads?.['server:default']?.url)
    : builds.find(item => item.channel === 'STABLE' && item.downloads?.['server:default']?.url)
      || (process.env.ALLOW_EXPERIMENTAL === '1' ? builds.find(item => item.downloads?.['server:default']?.url) : null)
  assert(build, `No matching Paper build for ${mcVersion}; set ALLOW_EXPERIMENTAL=1 if only experimental builds exist`)
  const artifact = build.downloads['server:default']
  const expected = artifact.checksums?.sha256
  let hash = fs.existsSync(cachedServerJar)
    ? crypto.createHash('sha256').update(await fsp.readFile(cachedServerJar)).digest('hex')
    : null
  if (!hash || (expected && hash !== expected)) {
    const jarResponse = await fetch(artifact.url, { headers })
    assert(jarResponse.ok, `Paper download failed: HTTP ${jarResponse.status}`)
    const temp = `${cachedServerJar}.download`
    await pipeline(Readable.fromWeb(jarResponse.body), fs.createWriteStream(temp))
    hash = crypto.createHash('sha256').update(await fsp.readFile(temp)).digest('hex')
    if (expected) assert(hash === expected, 'Paper SHA-256 mismatch')
    await fsp.rename(temp, cachedServerJar)
  }
  await fsp.copyFile(cachedServerJar, serverJar)
  report.paper = { build: build.id, channel: build.channel, sha256: hash }
}

async function downloadBridge() {
  if (botVersion === mcVersion) return
  const release = '5.12.1'
  for (const project of ['ViaVersion', 'ViaBackwards']) {
    const filename = `${project}-${release}.jar`
    const cached = path.join(repo, 'work/height-smoke/bridge', filename)
    await fsp.mkdir(path.dirname(cached), { recursive: true })
    if (!fs.existsSync(cached)) {
      const url = `https://github.com/ViaVersion/${project}/releases/download/${release}/${filename}`
      const response = await fetch(url, { headers: { 'User-Agent': 'world-height-limit-smoke/0.1' } })
      assert(response.ok, `${project} download failed: HTTP ${response.status}`)
      const temp = `${cached}.download`
      await pipeline(Readable.fromWeb(response.body), fs.createWriteStream(temp))
      await fsp.rename(temp, cached)
    }
    await fsp.copyFile(cached, path.join(lab, 'plugins', filename))
  }
  report.protocolBridge = `ViaVersion and ViaBackwards ${release}`
}

async function waitFor(predicate, message, ms = timeoutMs) {
  const started = Date.now()
  while (Date.now() - started < ms) {
    const result = predicate()
    if (result) return result
    await delay(250)
  }
  throw new Error(`Timed out: ${message}`)
}

async function startServer(phase, port) {
  const logPath = path.join(lab, `${phase}.console.log`)
  const output = fs.createWriteStream(logPath, { flags: 'w' })
  const child = spawn(java, ['-Xms512M', '-Xmx2G', '-jar', serverJar, '--nogui'], { cwd: lab, stdio: ['pipe', 'pipe', 'pipe'] })
  let buffer = ''
  let exited = false
  child.on('exit', () => { exited = true })
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {
    const data = chunk.toString()
    buffer += data
    output.write(data)
  })
  const send = command => child.stdin.write(`${command}\n`)
  try {
    await waitFor(() => {
      if (exited) throw new Error(`Server exited during ${phase}; see ${logPath}`)
      return /Done \([\d.]+s\)!/.test(buffer)
    }, `server ready in ${phase}`)
  } catch (error) {
    if (!exited) { send('stop'); await delay(2000); if (!exited) child.kill() }
    output.end()
    throw error
  }
  return {
    child, send, logPath,
    contains(text) { return buffer.includes(text) },
    async stop() {
      if (!exited) send('stop')
      await waitFor(() => exited, `server stop in ${phase}`, 30_000)
      output.end()
    }
  }
}

async function checkBlock(server, x, y, z, block, marker) {
  server.send(`execute if block ${x} ${y} ${z} minecraft:${block} run say ${marker}`)
  await waitFor(() => server.contains(marker), `block ${block} at ${x},${y},${z}`, 10_000)
}

async function connectBot(username, port) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username, version: botVersion, auth: 'offline', hideErrors: true })
  bot.on('error', error => {
    report.warnings ??= []
    report.warnings.push(`${username}: ${error.name}: ${error.message}`)
  })
  if (botVersion !== mcVersion) {
    bot.physicsEnabled = false
    const write = bot._client.write.bind(bot._client)
    const movement = new Set(['position', 'position_look', 'look', 'flying'])
    if (mcVersion === '26.3') {
      // 26.3 accepts at most one position packet per client tick. Mineflayer
      // omits client_tick_end, so its second movement packet gets the player
      // kicked. ViaBackwards forwards tick_end from the 26.1 protocol.
      bot._client.write = (name, ...args) => {
        const result = write(name, ...args)
        if (movement.has(name)) write('tick_end', {})
        return result
      }
      report.clientTickEndWorkaround = true
    } else {
      // Older translated clients can safely use server-side teleport here.
      bot._client.write = (name, ...args) => {
        if (movement.has(name)) return
        return write(name, ...args)
      }
    }
  }
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${username} spawn timeout`)), 30_000)
    bot.once('spawn', () => { clearTimeout(timer); resolve() })
    bot.once('error', error => { clearTimeout(timer); reject(error) })
    bot.once('kicked', reason => { clearTimeout(timer); reject(new Error(`${username} kicked: ${JSON.stringify(reason)}`)) })
  })
  return bot
}

async function placeAsPlayer(server, port, username, x, bypass) {
  const bot = await connectBot(username, port)
  try {
    if (bypass) server.send(`op ${username}`)
    server.send(`gamemode creative ${username}`)
    server.send(`forceload add ${x} 0`)
    await delay(1500)
    server.send(`setblock ${x} 1999 2 minecraft:stone`)
    server.send(`setblock ${x} 1999 0 minecraft:stone`)
    server.send(`setblock ${x} 2000 0 minecraft:air`)
    server.send(`tp ${username} ${x} 2000 2`)
    server.send(`give ${username} minecraft:stone 1`)
    await waitFor(() => Math.abs(bot.entity.position.y - 2000) < 3, `${username} teleported`, 15_000)
    const reference = new Vec3(x, 1999, 0)
    await waitFor(() => bot.blockAt(reference)?.name === 'stone', `${username} reference block visible`, 15_000)
    await waitFor(() => bot.blockAt(new Vec3(x, 2000, 0))?.name === 'air', `${username} empty target visible`, 15_000)
    const item = await waitFor(() => bot.inventory.items().find(entry => entry.name === 'stone'), `${username} stone in inventory`, 15_000)
    await bot.equip(item, 'hand')
    let placementError = null
    try { await bot.placeBlock(bot.blockAt(reference), new Vec3(0, 1, 0)) } catch (error) { placementError = error.message }
    await delay(1200)
    const diagnostic = {
      position: bot.entity.position.toString(),
      gameMode: bot.game.gameMode,
      heldItem: bot.heldItem?.name,
      referenceBlock: bot.blockAt(reference)?.name,
      targetBlock: bot.blockAt(new Vec3(x, 2000, 0))?.name,
      placementError
    }
    report.checks[`${username}Diagnostic`] = diagnostic
    const expected = bypass ? 'stone' : 'air'
    await checkBlock(server, x, 2000, 0, expected, `${username}_${expected}_CONFIRMED`)
    return { expected, observed: expected, placementError }
  } finally { bot.quit() }
}

async function main() {
  await fsp.mkdir(lab, { recursive: true })
  await fsp.mkdir(versionRoot, { recursive: true })
  if (!engineProbeOnly) {
    assert(fs.existsSync(pluginJar), `Plugin JAR missing: ${pluginJar}`)
    report.pluginSha256 = crypto.createHash('sha256').update(await fsp.readFile(pluginJar)).digest('hex')
  }
  const port = await freePort()
  await fsp.writeFile(path.join(lab, 'eula.txt'), 'eula=true\n')
  await fsp.writeFile(path.join(lab, 'server.properties'), [
    'server-ip=127.0.0.1', `server-port=${port}`, 'online-mode=false', 'white-list=false',
    'spawn-protection=0', 'enable-rcon=false', 'view-distance=4', 'simulation-distance=4',
    'max-players=4', 'motd=WorldHeightLimit isolated smoke test', ''
  ].join('\n'))
  await fsp.mkdir(path.join(lab, 'plugins'), { recursive: true })
  if (!engineProbeOnly) {
    await fsp.copyFile(pluginJar, path.join(lab, 'plugins', 'WorldHeightLimit.jar'))
    if (observerJar) await fsp.copyFile(observerJar, path.join(lab, 'plugins', 'HeightSmokeObserver.jar'))
    await downloadBridge()
  }
  await downloadPaper()
  if (vanillaServerJar) {
    assert(fs.existsSync(vanillaServerJar), `Vanilla server JAR missing: ${vanillaServerJar}`)
    await fsp.mkdir(path.join(lab, 'cache'), { recursive: true })
    await fsp.copyFile(vanillaServerJar, path.join(lab, 'cache', `mojang_${mcVersion}.jar`))
    report.vanillaServerSha256 = crypto.createHash('sha256').update(await fsp.readFile(vanillaServerJar)).digest('hex')
  }

  if (engineProbeOnly) {
    const server = await startServer('engine-probe', port)
    try {
      server.send('forceload add 0 0')
      await delay(1500)
      server.send('setblock 0 2000 0 minecraft:stone')
      const outcome = await waitFor(() => {
        if (server.contains('That position is out of this world')) return 'REJECTED'
        if (server.contains('Changed the block at 0, 2000, 0')) return 'ACCEPTED'
        return null
      }, 'vanilla world-height probe', 15_000)
      report.checks.vanillaSetblockY2000 = outcome
      report.status = 'PROBE'
    } finally { await server.stop() }
    return
  }

  const first = await startServer('bootstrap', port)
  report.checks.bootstrap = 'PASS'
  await first.stop()
  const pack = path.join(lab, 'world/datapacks')
  assert(fs.existsSync(pack), `Plugin did not create world datapacks: ${pack}`)
  report.checks.datapackGenerated = 'PASS'

  const second = await startServer('placement', port)
  try {
    report.checks.serverRestart = 'PASS'
    report.checks.allowedPlacement = await placeAsPlayer(second, port, 'HeightProbe', 0, true)
    report.checks.deniedPlacement = await placeAsPlayer(second, port, 'HeightNoPerm', 3, false)
  } finally { await second.stop() }

  const third = await startServer('persistence', port)
  try {
    third.send('forceload add 0 0')
    await delay(1500)
    await checkBlock(third, 0, 2000, 0, 'stone', 'HEIGHT_PERSISTED')
    report.checks.persistence = 'PASS'
  } finally { await third.stop() }
  report.status = 'PASS'
}

main().catch(error => {
  report.status = 'FAIL'
  report.error = error.stack || String(error)
  process.exitCode = 1
}).finally(async () => {
  report.finishedAt = new Date().toISOString()
  await fsp.mkdir(lab, { recursive: true })
  await fsp.writeFile(reportPath, JSON.stringify(report, null, 2) + os.EOL)
  process.stdout.write(`${JSON.stringify({ status: report.status, report: reportPath, error: report.error }, null, 2)}\n`)
})
