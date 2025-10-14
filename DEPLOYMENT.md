# Deployment Guide

This guide walks you through publishing your code to GitHub and automatically building/publishing Docker images to Docker Hub.

## Prerequisites

1. A GitHub account
2. A Docker Hub account (sign up at https://hub.docker.com)
3. Git installed and configured locally

## Step 1: Configure Git (if not already done)

```bash
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

## Step 2: Create Initial Commit

```bash
cd /home/pokkew/projects/doc-to-pdf-converter
git add .
git commit -m "Initial commit: Document to PDF converter with multiple file support"
```

## Step 3: Create GitHub Repository

1. Go to https://github.com/new
2. Repository name: `doc-to-pdf-converter`
3. Description: "Web-based document to PDF converter with multiple file support"
4. Make it Public or Private (your choice)
5. **Do NOT** initialize with README, .gitignore, or license (we already have these)
6. Click "Create repository"

## Step 4: Push Code to GitHub

After creating the repository, GitHub will show you commands. Use these:

```bash
cd /home/pokkew/projects/doc-to-pdf-converter
git remote add origin https://github.com/YOUR_USERNAME/doc-to-pdf-converter.git
git push -u origin main
```

Replace `YOUR_USERNAME` with your actual GitHub username.

## Step 5: Set Up Docker Hub Secrets in GitHub

1. Go to your GitHub repository page
2. Click on **Settings** tab
3. In the left sidebar, click **Secrets and variables** → **Actions**
4. Click **New repository secret**
5. Add two secrets:

   **Secret 1:**
   - Name: `DOCKER_USERNAME`
   - Value: Your Docker Hub username

   **Secret 2:**
   - Name: `DOCKER_PASSWORD`
   - Value: Your Docker Hub password or access token (recommended)

### Creating a Docker Hub Access Token (Recommended)

Instead of using your password, create an access token:

1. Go to https://hub.docker.com/settings/security
2. Click **New Access Token**
3. Description: "GitHub Actions"
4. Access permissions: Read, Write, Delete
5. Click **Generate**
6. Copy the token and use it as `DOCKER_PASSWORD` in GitHub secrets

## Step 6: Create Docker Hub Repository

1. Go to https://hub.docker.com
2. Click **Repositories** → **Create Repository**
3. Repository name: `doc-to-pdf-converter`
4. Description: "Web-based document to PDF converter"
5. Visibility: Public
6. Click **Create**

## Step 7: Trigger First Build

The GitHub Actions workflow will automatically run when you push to the `main` branch. To trigger it:

```bash
# Make a small change (or just trigger a rebuild)
git commit --allow-empty -m "Trigger Docker build"
git push origin main
```

## Step 8: Monitor the Build

1. Go to your GitHub repository
2. Click the **Actions** tab
3. You'll see the workflow running: "Build and Push to Docker Hub"
4. Click on it to see the build progress
5. Wait for it to complete (usually 5-10 minutes for the first build)

## Step 9: Verify Docker Hub Publication

1. Go to https://hub.docker.com/r/YOUR_USERNAME/doc-to-pdf-converter
2. You should see your image with the `latest` tag
3. The "Tags" tab will show all available versions

## Using Your Published Image

Once published, anyone can use your image:

```bash
docker pull YOUR_USERNAME/doc-to-pdf-converter:latest
docker run -p 3000:3000 YOUR_USERNAME/doc-to-pdf-converter:latest
```

## Automated Publishing Workflow

The GitHub Actions workflow automatically:

- **On push to main**: Builds and publishes as `latest`
- **On version tags** (e.g., `v1.0.0`): Builds and publishes with semantic version tags
- **On pull requests**: Builds but doesn't push (validation only)

### Creating a Version Release

To publish a specific version:

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

This will create Docker images with tags:
- `YOUR_USERNAME/doc-to-pdf-converter:1.0.0`
- `YOUR_USERNAME/doc-to-pdf-converter:1.0`
- `YOUR_USERNAME/doc-to-pdf-converter:1`
- `YOUR_USERNAME/doc-to-pdf-converter:latest`

## Troubleshooting

### Build Fails with Authentication Error

- Check that `DOCKER_USERNAME` and `DOCKER_PASSWORD` secrets are correctly set
- If using a token, ensure it has Read/Write/Delete permissions
- Token must be the actual token string, not the token ID

### Build Succeeds but Image Not on Docker Hub

- Check that the Docker Hub repository exists
- Verify the repository name in `.github/workflows/docker-publish.yml` matches your Docker Hub username
- Check build logs in GitHub Actions for error messages

### Git Push Fails

- Make sure you've replaced `YOUR_USERNAME` with your actual GitHub username
- If using HTTPS, you may need a Personal Access Token instead of password (GitHub requirement)
- Generate token at: https://github.com/settings/tokens

## Updating README with Correct Docker Hub URL

After publishing, update the README.md to reflect your actual Docker Hub username:

1. Edit `README.md`
2. Replace `pokkew/doc-to-pdf-converter` with `YOUR_USERNAME/doc-to-pdf-converter`
3. Commit and push the changes

```bash
# Example:
sed -i 's/pokkew\/doc-to-pdf-converter/YOUR_USERNAME\/doc-to-pdf-converter/g' README.md
git add README.md
git commit -m "Update Docker Hub username in README"
git push origin main
```

## Next Steps

- Add a license file (e.g., MIT, Apache 2.0)
- Add badges to README (build status, Docker pulls, etc.)
- Set up automated testing
- Create a CHANGELOG.md for version history
- Add more documentation for contributors

## Support

If you encounter issues:
1. Check GitHub Actions logs for detailed error messages
2. Verify all secrets are correctly configured
3. Ensure Docker Hub repository exists and is accessible
4. Check that the repository is public if you want public images
