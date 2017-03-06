// Jobs
def generateLoadCartridgeJob = workflowJob("/Load_Cartridge")

generateLoadCartridgeJob.with {
    parameters {
        stringParam("WORKSPACE_NAME","Example_Workspace","Name of the workspace to load cartridge in (either existing or new).")
        stringParam("PROJECT_NAME","Example_Project","Name of the project to load cartridge in (either existing or new).")
        stringParam("SCM_NAMESPACE", "", "(Optional) The namespace for your SCM provider which will prefix your created repositories.")
        extensibleChoiceParameterDefinition {
            name('CARTRIDGE_CLONE_URL')
            choiceListProvider {
                systemGroovyChoiceListProvider {
                    scriptText('''import jenkins.model.*
nodes = Jenkins.instance.globalNodeProperties
nodes.getAll(hudson.slaves.EnvironmentVariablesNodeProperty.class)
envVars = nodes[0].envVars
def URLS = envVars['CARTRIDGE_SOURCES'];
if (URLS == null) {
    println "[ERROR] CARTRIDGE_SOURCES Jenkins environment variable has not been set";
    return ['Type the cartridge URL (or add CARTRIDGE_SOURCES as a Jenkins environment variable if you wish to see a list here)'];
}
if (URLS.length() < 11) {
    println "[ERROR] CARTRIDGE_SOURCES Jenkins environment variable does not seem to contain valid URLs";
    return ['Type the cartridge URL (the CARTRIDGE_SOURCES Jenkins environment variable does not seem valid)'];
}
def cartridge_urls = [];
URLS.split(';').each{ source_url ->
try {
    def html = source_url.toURL().text;
    html.eachLine { line ->
    if (line.contains("url:")) {
        def url = line.substring(line.indexOf("\\"") + 1, line.lastIndexOf("\\""))
        cartridge_urls.add(url)
    }
  }
}
catch (UnknownHostException e) {
    cartridge_urls.add("[ERROR] Provided URL was not found: ${source_url}");
    println "[ERROR] Provided URL was not found: ${source_url}";
}
catch (Exception e) {
    cartridge_urls.add("[ERROR] Unknown error while processing: ${source_url}");
    println "[ERROR] Unknown error while processing: ${source_url}";
}
}
return cartridge_urls;
''')
                    defaultChoice('Top')
                    usePredefinedVariables(false)
                }
            }
            editable(true)
            description('Cartridge URL to load.')
        }
        activeChoiceParam('SCM_PROVIDER') {
            description('Your chosen SCM Provider and the appropriate cloning protocol')
            filterable()
            choiceType('SINGLE_SELECT')
            scriptlerScript('retrieve_scm_props.groovy')
        }
        stringParam('CARTRIDGE_FOLDER', '', 'The folder within the project namespace where your cartridge will be loaded into.')
        stringParam('FOLDER_DISPLAY_NAME', '', 'Display name of the folder where the cartridge is loaded.')
        stringParam('FOLDER_DESCRIPTION', '', 'Description of the folder where the cartridge is loaded.')
        booleanParam('ENABLE_CODE_REVIEW', false, 'Enables Code Reviewing for the selected cartridge')
        booleanParam('OVERWRITE_REPOS', false, 'If ticked, existing code repositories (previously loaded by the cartridge) will be overwritten. For first time cartridge runs, this property is redundant and will perform the same behavior regardless.')
    }
    label('''docker''')
    properties {
        rebuild {
            autoRebuild(false)
            rebuildDisabled(false)
        }
    }
    definition {
        cps {
            script('''def customNamespaceEnabled = true;
if("${SCM_NAMESPACE}" == null || "${SCM_NAMESPACE}".equals("")) {
    customNamespaceEnabled = false;
}

// Setup Workspace Folder
build job: 'Workspace_Management/Generate_Workspace', parameters: [[$class: 'StringParameterValue', name: 'WORKSPACE_NAME', value: "${WORKSPACE_NAME}"]]

// Setup Project Folder
build job: "${WORKSPACE_NAME}/Project_Management/Generate_Project", parameters: [[$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${PROJECT_NAME}"], [$class: 'BooleanParameterValue', name: 'CUSTOM_SCM_NAMESPACE', value: customNamespaceEnabled]]

// Load Cartridge
retry(5)
{
    build job: "${WORKSPACE_NAME}/${PROJECT_NAME}/Cartridge_Management/Load_Cartridge", parameters: [[$class: 'StringParameterValue', name: 'CARTRIDGE_FOLDER', value: "${CARTRIDGE_FOLDER}"], [$class: 'StringParameterValue', name: 'FOLDER_DISPLAY_NAME', value: "${FOLDER_DISPLAY_NAME}"], [$class: 'StringParameterValue', name: 'FOLDER_DESCRIPTION', value: "${FOLDER_DESCRIPTION}"], [$class: 'BooleanParameterValue', name: 'ENABLE_CODE_REVIEW', value: "${ENABLE_CODE_REVIEW}".toBoolean()], [$class: 'BooleanParameterValue', name: 'OVERWRITE_REPOS', value: "${OVERWRITE_REPOS}".toBoolean()], [$class: 'StringParameterValue', name: 'CARTRIDGE_CLONE_URL', value: "${CARTRIDGE_CLONE_URL}"], [$class: 'StringParameterValue', name: 'SCM_NAMESPACE', value: "${SCM_NAMESPACE}"], [$class: 'StringParameterValue', name: 'SCM_PROVIDER', value: "${SCM_PROVIDER}"]]
}''')
            sandbox()
        }
    }
}