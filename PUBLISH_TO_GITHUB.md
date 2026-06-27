# Publish To GitHub

This project is now prepared for a public source repository.

## Before you publish

1. Add a license file.
2. Check that `README.md` matches the public name you want to use.
3. Make sure there is nothing in local folders like `work/` or `outputs/` that you still want to keep private.

## Recommended license options

- `MIT` if you want very permissive reuse
- `GPL-3.0` if you want derivatives to stay open source

Without a license, people can view the code on GitHub, but they do not automatically have permission to reuse it.

## Create a local git repository

```powershell
git init
git add .
git commit -m "Initial public source release"
```

## Push to GitHub

Create an empty repository on GitHub first, then run:

```powershell
git branch -M main
git remote add origin https://github.com/YOUR_NAME/YOUR_REPOSITORY.git
git push -u origin main
```

## What is already prepared

- `.gitignore` excludes build artifacts, local jars, and temporary files
- `.github/workflows/build.yml` builds the plugin automatically on push and pull request
- `.github/ISSUE_TEMPLATE/bug_report.yml` adds a clean public bug report form
