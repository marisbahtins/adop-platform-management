// Constants
def platformToolsGitURL = "ssh://jenkins@gerrit:29418/platform-management"

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def workspaceFolder = folder(workspaceFolderName)

def projectManagementFolderName= workspaceFolderName + "/Project_Management"
def projectManagementFolder = folder(projectManagementFolderName) { displayName('Project Management') }

// Jobs
def generateProjectJob = freeStyleJob(projectManagementFolderName + "/Generate_Project")

def adopLdapEnabled = '';

try{
  adopLdapEnabled = "${ADOP_LDAP_ENABLED}".toBoolean();
}catch(MissingPropertyException ex){
  adopLdapEnabled = true;
}

// Setup Generate_Project
generateProjectJob.with{
    parameters{
        stringParam("PROJECT_NAME","","The name of the project to be generated.")
        booleanParam('CUSTOM_SCM_NAMESPACE', false, 'Enables the option to provide a custom project namespace for your SCM provider')
        stringParam("ADMIN_USERS","","The list of users' email addresses that should be setup initially as admin. They will have full access to all jobs within the project.")
        stringParam("DEVELOPER_USERS","","The list of users' email addresses that should be setup initially as developers. They will have full access to all non-admin jobs within the project.")
        stringParam("VIEWER_USERS","","The list of users' email addresses that should be setup initially as viewers. They will have read-only access to all non-admin jobs within the project.")
    }
    label("ldap")
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        if(adopLdapEnabled == true)
        {
          environmentVariables
          {
              env('DC', "${LDAP_ROOTDN}")
              env('OU_GROUPS','ou=groups')
              env('OU_PEOPLE','ou=people')
              env('OUTPUT_FILE','output.ldif')
          }
          credentialsBinding
          {
              usernamePassword("LDAP_ADMIN_USER", "LDAP_ADMIN_PASSWORD", "adop-ldap-admin")
          }
        }
        sshAgent("adop-jenkins-master")
    }
    steps
    {
      shell('''#!/bin/bash
# Validate Variables
pattern=" |'"
if [[ "${WORKSPACE_NAME}" =~ ${pattern} ]]; then
    echo "WORKSPACE_NAME contains a space, please replace with an underscore - exiting..."
    exit 1
fi''')
        conditionalSteps
        {
          condition
          {
              shell('''#!/bin/bash
if [ "${ADOP_ACL_ENABLED}" == "false" ]
then
  exit 1
fi
exit 0
              ''')
          }
          runner('DontRun')
            steps {
                systemGroovyScriptFile('${WORKSPACE}/workspaces/groovy/acl_admin.groovy')
                systemGroovyScriptFile('${WORKSPACE}/workspaces/groovy/acl_developer.groovy')
                systemGroovyScriptFile('${WORKSPACE}/workspaces/groovy/acl_viewer.groovy')
            }
        }
        if(adopLdapEnabled == true){
          shell('''
 # LDAP
 ${WORKSPACE}/common/ldap/generate_role.sh -r "admin" -n "${WORKSPACE_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${ADMIN_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
 ${WORKSPACE}/common/ldap/generate_role.sh -r "developer" -n "${WORKSPACE_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${DEVELOPER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
 ${WORKSPACE}/common/ldap/generate_role.sh -r "viewer" -n "${WORKSPACE_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${VIEWER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"

 set +e
 ${WORKSPACE}/common/ldap/load_ldif.sh -h ldap -u "${LDAP_ADMIN_USER}" -p "${LDAP_ADMIN_PASSWORD}" -b "${DC}" -f "${OUTPUT_FILE}"
 set -e

 ADMIN_USERS=$(echo ${ADMIN_USERS} | tr ',' ' ')
 DEVELOPER_USERS=$(echo ${DEVELOPER_USERS} | tr ',' ' ')
 VIEWER_USERS=$(echo ${VIEWER_USERS} | tr ',' ' ')

 # Gerrit
 for user in $ADMIN_USERS $DEVELOPER_USERS $VIEWER_USERS
 do
     username=$(echo ${user} | cut -d'@' -f1)
     ${WORKSPACE}/common/gerrit/create_user.sh -g http://gerrit:8080/gerrit -u "${username}" -p "${username}"
 done

 # Gerrit
 source ${WORKSPACE}/projects/gerrit/configure.sh
 # Generate second permission repo with enabled code-review
 source ${WORKSPACE}/projects/gerrit/configure.sh -r permissions-with-review
             ''')
        }
        dsl {
          external("projects/jobs/**/*.groovy")
        }
    }
    scm {
        git {
            remote {
                name("origin")
                url("${platformToolsGitURL}")
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
}
