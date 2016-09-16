// Constants
def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def loadPlatformExtensionCollectionJob = workflowJob(platformManagementFolderName + "/Load_Platform_Extension_Collection")


// Setup Load_Cartridge Collection
loadPlatformExtensionCollectionJob.with{
    description("This job loads a collection of platform extensions.")
    parameters{
        stringParam('COLLECTION_URL', '', 'URL to a JSON file defining your platform extension collection.')
        credentialsParam("CREDENTIALS"){
            type('com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl')
            defaultValue('adop-default')
            description('Platform extension credentials. Note: Leave at adop-default if credentials are not required.')
        }
    }
    properties {
        rebuild {
            autoRebuild(false)
            rebuildDisabled(false)
        }
    }
	definition {
        cps {
            script('''node {

    sh("wget ${COLLECTION_URL} -O collection.json")

    println "Reading in values from file..."
    extensions = parseJSON(readFile('collection.json'))

    println(extensions);
    println "Obtained values locally...";

    extensionCount = extensions.size
    println "Number of platform extensions: ${extensionCount}"

    // For loop iterating over the data map obtained from the provided JSON file
    for (int i = 0; i < extensionCount; i++) {
        def extension = extensions.get(i);
        println("Platform Extension URL: " + extension.url)
        build job: '/Platform_Management/Load_Platform_Extension', parameters: [[$class: 'StringParameterValue', name: 'GIT_URL', value: extension.url], [$class: 'StringParameterValue', name: 'GIT_REF', value: 'master'], [$class: 'CredentialsParameterValue', name: 'CREDENTIALS', value: "${CREDENTIALS}"]]
    }

}

@NonCPS
    def parseJSON(text) {
    def slurper = new groovy.json.JsonSlurper();
    Map data = slurper.parseText(text)
    slurper = null

    def extensions = []
    for ( i = 0 ; i < data.extensions.size; i++ ) {
        String url = data.extensions[i].url
        extensions[i] = ['url' : url]
    }

    data = null

    return extensions
}
            ''')
            sandbox()
        }
    }
}
