#!/usr/bin/groovy

node() {
    def nodejs = tool 'NodeJS_8'
    def root = pwd()
    def nexusRepoUrl = env.NEXUS_REPO_URL ?: "https://nexus.devops.geointservices.io/content/repositories/eventkit-Releases"
    def nexusCreds = env.NEXUS_CREDS_VARNAME ?: "nexus-deployment"
    def dataset = env.DATASET ?: "whosonfirst"  //should be a dataset as listed in the imports block of the pelias.json file.

    stage('Setup') {
        sh "rm -rf repo"
        if(dataset.equalsIgnoreCase("whosonfirst")) {
            sh "git clone https://github.com/eventkit/pelias-whosonfirst repo"
            //temporary step
            dir("repo") {
                sh "git checkout addUpdates"
            }
        }
        if(dataset.equalsIgnoreCase("geographicnames")) {
            sh "git clone https://github.com/eventkit/pelias-gndb repo"
        }
        if(dataset.equalsIgnoreCase("geonames")) {
            sh "git clone https://github.com/pelias/geonames repo"
        }
        if(dataset.equalsIgnoreCase("polyline")) {
            sh "git clone https://github.com/eventkit/pelias-polylines repo"
        }
        if(dataset.equalsIgnoreCase("openstreetmap")) {
            sh "git clone https://github.com/pelias/openstreetmap repo"
        }
        if(dataset.equalsIgnoreCase("openaddresses")) {
            sh "git clone https://github.com/pelias/openaddresses repo"
        }
    }

    stage("Install Downloader") {
        dir("repo") {
            withEnv(["PATH+=${nodejs}/bin",
                     "NPM_CONFIG_CACHE=${root}/.npmcache"]) {
                sh "npm install"
            }
        }
    }

    stage("Download Data") {
        dir("repo") {

            writeFile([
                    file: './pelias.json',
                    text: """{
  "imports": {
    "whosonfirst": {
      "datapath": "./peliasData",
      "importVenues": true,
      "importPostalcodes": true,
      "sqlite": true,
      "polygons": true
    },
    "openstreetmap": {
      "datapath": "./peliasData",
      "importVenues": true
    },
    "geographicnames": {
      "datapath": "./peliasData",
      "countryCode": "ALL"
    },
    "geonames": {
      "datapath": "./peliasData",
      "countryCode": "ALL"
    },
    "openaddresses": {
      "datapath": "./peliasData"
    },
    "polyline": {
      "datapath": "./peliasData"
    }
  }
}
"""])
            withEnv(["PATH+=${nodejs}/bin",
                     "NPM_CONFIG_CACHE=${root}/.npmcache",
                     "PELIAS_CONFIG=./pelias.json"]) {
                // Clean up old data.
                sh "rm -rf peliasData"
                sh "mkdir -p peliasData"
                sh "chmod 777 peliasData"
                sh "ls -al"
                sh "npm run download"
            }
        }
    }

    stage("Archive Data") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: nexusCreds, usernameVariable: 'NEXUSUSER', passwordVariable: 'NEXUSPASS']]) {
            Date date = new Date()
            String formattedDate = date.format("yyyy-MM-dd")
            dir("repo/peliasData") {
                sh """tar -zcf $dataset-data-${formattedDate}.tar.gz *"""
                sh """
            ls -al
            for f in \$(find . -type f -name '*.tar.gz'); do
              md5sum \$f > \$f.md5
              shasum -a 1 \$f > \$f.sha1  
              curl -v -T "\$f{"",".md5",".sha1"}" -u \$NEXUSUSER:\$NEXUSPASS $nexusRepoUrl/data/$dataset/
            done
            """
            }
        }
    }

    stage("Clean Up"){
        sh "rm -rf repo"
    }
}
