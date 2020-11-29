import groovy.json.JsonSlurper

// Config
def projectDomain = "wwww.coinsvista.com"

def getTime(ts) {
    return new Date( (ts as long)).format('HH:mm:ss dd-MM-yyyy')
}

try {
    List<String> options = new ArrayList<String>()
    options.add('release')

    def s3Text = ("aws s3 ls s3://" + projectDomain).execute().text
    def s3Lines = []
    s3Text.eachLine { line, i ->
        s3Lines[i] = line
    }
    s3Lines = s3Lines.reverse()

    s3Lines.eachWithIndex { line, index ->
        if (line.contains('build_')) {
        def parts = line.split("\\s+")
        def myBuild = parts[2].replaceAll("/", "");
        def buildNameParts = myBuild.split("_");
        options.add('rollback' + ' ' + myBuild + ' (' + getTime(buildNameParts[1]) + ')')
    }
}
    return options
} catch (Exception e) {
    print "There was a problem fetching the options"
}