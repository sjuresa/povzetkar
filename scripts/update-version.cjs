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

const rootDir = path.join(__dirname, '..');

try {
    // 1. Update Maven versions (Root and Submodules)
    console.log(`\n📦 Updating Maven versions to ${newVersion}...`);
    const mvnw = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
    
    // We run versions:set on the root. The plugin handles submodule parent versions automatically.
    execSync(`${mvnw} versions:set -DnewVersion=${newVersion} -DgenerateBackupPoms=false`, { 
        cwd: rootDir,
        stdio: 'inherit' 
    });

    // 2. Update package.json version
    console.log(`\nJSON Updating package.json version to ${newVersion}...`);
    const packageJsonPath = path.join(rootDir, 'package.json');
    
    if (fs.existsSync(packageJsonPath)) {
        const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
        packageJson.version = newVersion;
        fs.writeFileSync(packageJsonPath, JSON.stringify(packageJson, null, 2) + '\n');
        console.log('✅ package.json updated.');
    } else {
        console.warn('⚠️ Warning: package.json not found in root.');
    }

    // 3. Update frontend/package.json version (if exists)
    const frontendPackageJsonPath = path.join(rootDir, 'frontend', 'package.json');
    if (fs.existsSync(frontendPackageJsonPath)) {
        console.log(`\nJSON Updating frontend/package.json version to ${newVersion}...`);
        const frontendPackageJson = JSON.parse(fs.readFileSync(frontendPackageJsonPath, 'utf8'));
        frontendPackageJson.version = newVersion;
        fs.writeFileSync(frontendPackageJsonPath, JSON.stringify(frontendPackageJson, null, 2) + '\n');
        console.log('✅ frontend/package.json updated.');
    }

    console.log('\n✨ Successfully synchronized all version files.');

} catch (error) {
    console.error('\n❌ Error during version update:');
    console.error(error.message);
    process.exit(1);
}
