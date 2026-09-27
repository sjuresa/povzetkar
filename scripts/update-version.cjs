/**
 * Cross-platform script to synchronize project versions across Maven and NPM.
 * Usage: node scripts/update-version.cjs <new-version>
 */
const fs = require('node:fs');
const path = require('node:path');
const { execSync } = require('node:child_process');

const newVersion = process.argv[2];

if (!newVersion) {
    console.error('❌ Error: No version provided.');
    console.log('Usage: node scripts/update-version.cjs <new-version>');
    process.exit(1);
}

const semverRegex = /^\d+\.\d+\.\d+(-[a-zA-Z0-9.-]+)?$/;
if (!semverRegex.test(newVersion)) {
    console.error('❌ Error: Invalid version format: "' + newVersion + '". Please use a valid semantic version (e.g., 1.0.0 or 1.0.0-SNAPSHOT).');
    process.exit(1);
}

const rootDir = path.join(__dirname, '..');

// Handles package.json and package-lock.json (whose root package repeats the version under packages['']).
// Sets only the version fields — `npm install --package-lock-only` would also re-resolve the tree.
const updatePackageVersion = (filePath, version) => {
    const relativePath = path.relative(rootDir, filePath);
    if (fs.existsSync(filePath)) {
        console.log('\nUpdating ' + relativePath + ' version to ' + version + '...');
        const packageContent = fs.readFileSync(filePath, 'utf8');
        const packageJson = JSON.parse(packageContent);
        packageJson.version = version;
        if (packageJson.packages?.['']) {
            packageJson.packages[''].version = version;
        }
        fs.writeFileSync(filePath, JSON.stringify(packageJson, null, 2) + '\n');
        console.log('✅ ' + relativePath + ' updated.');
    } else {
        console.warn('⚠️ Warning: ' + relativePath + ' not found.');
    }
};

try {
    // 1. Update Maven versions (Root and Submodules)
    console.log('\n📦 Updating Maven versions to ' + newVersion + '...');
    // Explicit relative path: Windows shells with NoDefaultCurrentDirectoryInExePath do not search the cwd
    const mvnw = process.platform === 'win32' ? String.raw`.\mvnw.cmd` : './mvnw';

    // We run versions:set on the root. The plugin handles submodule parent versions automatically.
    execSync(mvnw + ' versions:set -DnewVersion=' + newVersion + ' -DgenerateBackupPoms=false', {
        cwd: rootDir,
        stdio: 'inherit'
    });

    // 2. Update package.json and package-lock.json versions
    updatePackageVersion(path.join(rootDir, 'package.json'), newVersion);
    updatePackageVersion(path.join(rootDir, 'frontend', 'package.json'), newVersion);
    updatePackageVersion(path.join(rootDir, 'package-lock.json'), newVersion);
    updatePackageVersion(path.join(rootDir, 'frontend', 'package-lock.json'), newVersion);

    console.log('\n✨ Successfully synchronized all version files.');

} catch (error) {
    console.error('\n❌ Error during version update:');
    console.error(error.message);
    process.exit(1);
}
