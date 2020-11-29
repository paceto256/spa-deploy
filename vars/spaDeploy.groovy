import groovy.json.JsonSlurperClassic
import static groovy.io.FileType.DIRECTORIES

def getReleaseCmd(projectDomain, buildName) {
    return "aws s3 cp s3://" + projectDomain + "/" + buildName + "/index.html s3://" + projectDomain + "/index.html --acl public-read --metadata-directive REPLACE --cache-control max-age=0,no-cache,no-store,must-revalidate"
}

def generate() {
    return new Date().format("yyyyMMdd-HH:mm:ss.SSS", TimeZone.getTimeZone('UTC'));
}

def gitCmd(cmd, sshKey) {
    return "ssh-agent bash -c 'ssh-add /var/lib/jenkins/.ssh/"+sshKey+"; " + cmd + " '";
}

def call(Map params) {
    //def workspacePath = "/var/jenkins_home/workspace/";
    def workspacePath = "/var/lib/jenkins/workspace/";

    def projectPath = workspacePath + params.projectName;
    def fromEmail = params.projectName + '@jenkins';
    def appEnv = params.scriptParams.appEnv ? params.scriptParams.appEnv : params.appEnv;
    def branch = params.scriptParams.branch ? params.scriptParams.branch : params.branch;
    def sshKey = params.sshKey ? params.sshKey : 'id_rsa';

    def actionParts = params.scriptParams.action.split("\\s+");
    def repoId = params.gitRepo.split('/').last().split("\\.")[0];
    def buildName = '';
    def patchFile = '';

    if (actionParts[0] == 'release') {
        def command1 = "cd " + workspacePath;

        command1 += ' && . ~/.nvm/nvm.sh ';

        if (params.scriptParams.updateNPM) {
            command1 += " && rm -rf " + projectPath;

            command1 += " && " + gitCmd("git clone " + params.gitRepo + " " + projectPath, sshKey)
        }

        command1 += " && cd " + projectPath;
        command1 += " && " + gitCmd("git checkout " + branch, sshKey);
        command1 += " && " + gitCmd("git fetch origin ", sshKey);
        command1 += " && " + gitCmd("git reset --hard origin/" + branch, sshKey);
        command1 += " && " + gitCmd("git pull origin " + branch, sshKey);

        if (params.scriptParams.version != 'none') {
            command1 += " && " + gitCmd("git fetch --tags ", sshKey);
        }

        if (params.scriptParams.updateNPM) {
            command1 += " && npm install ";
        }

        command1 += " && rm -rf dist ";

        if (params.scriptParams.version != 'none') {
            if(fileExists(projectPath + '/package-lock.json')) {
                command1 += " && " + gitCmd("git checkout package-lock.json ", sshKey);
            }

            command1 += " && npm version " + params.scriptParams.version;
        }

        command1 += " && npm run build:" + appEnv;

        if (params.scriptParams.version != 'none') {
            command1 += " && " + gitCmd("git pull origin " + branch, sshKey);
            command1 += " && " + gitCmd("git push --tags", sshKey);
            command1 += " && " + gitCmd("git push origin " + branch, sshKey);
        }

        sh command1;

        new File(projectPath + '/dist').eachFileRecurse (DIRECTORIES) { file ->
            if (file.name.contains('build_')) {
                buildName = file.name;
            }
        }

        if (buildName == '') {
            error("Unable to fetch buildName. Because of jira/groovy bug there should be only one folder in the /dist")
        }

        def command2 = "cd " + projectPath;

        command2 += " && aws s3 sync ./dist s3://" + params.projectDomain +
            "/ --acl public-read --exclude='index.html' ";
        command2 += " && aws s3 cp ./dist/index.html s3://" +
            params.projectDomain + "/" + buildName + "/index.html --acl public-read ";
        command2 += " && " + getReleaseCmd(params.projectDomain, buildName);

        sh command2;
    } else if (actionParts[0] == 'rollback') {
        buildName = actionParts[1];
        sh getReleaseCmd(params.projectDomain, buildName)
    }

    def packageJSON = readFile(file:projectPath + '/package.json');
    def packageData = new JsonSlurperClassic().parseText(packageJSON);

    emailext (
        from: fromEmail,
        subject: "jenkins - " + params.projectName + " " + actionParts[0] + " " + buildName,
        body: "url: https://" + params.projectDomain + '\n' +
        "env: " + appEnv + '\n' +
        "branch: " + branch + '\n',
        to: params.botEmail
    )
}
