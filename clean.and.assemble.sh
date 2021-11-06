#!/usr/bin/env bash

#export JAVA_HOME=/usr/lib/jvm/java-8-openjdk
#export JAVA_HOME=/usr/lib/jvm/java-10-openjdk
export JAVA_HOME=/osshare/software/oracle/java-8-oracle

#set -x
mffile=$(grep Manifest build.gradle | egrep -v '(ALL)' | grep -v '//' | cut -d "'" -f 2)
if [[ -z "${mffile}" ]]; then
    echo "Could not determine manifest file by looking in build.gradle"
    exit 1
fi
pkg=$(grep package= ${mffile} | perl -ne 's~.*"([a-z\.]+)".*~$1~; print')

productFlavor="phoneTabletPost23"
relapk=$(find . -name "*${productFlavor}-release.apk")
if [[ -e ${relapk} ]]; then
    oldFileTime=$(find ${relapk} -maxdepth 0 -printf "%Ty%Tm%Td%TH%TM.%.2TS")

    echo "Comparing changetime of files against ${relapk} (${oldFileTime})"
    changedFiles="$(find . -newer ${relapk} | egrep -v '(intermediates)' | egrep '\.(java|xml|md|gradle)' | egrep -v '(\.idea/|/\.gradle/|/generated/|/reports/)')"
else
    changedFiles="No release apk to compare with"
fi

if [[ ${versionCodeX} -ne ${vcFromManifest} ]]; then
    echo "Not changing MANIFEST anymore" > /dev/null
    #sed -i "s~\(versionCode=.\)[0-9]*\(.\)~\1${versionCodeX}\2~" ${mffile}
fi

# check if a new version code for the brand for which we will be creating an apk is specified
brand=$(egrep 'Brand\s+brand\s*=\s*Brand\.' src/com/doubleyellow/scoreboard/Brand.java | perl -ne 's~.*Brand\.(\w+);.*~$1~; print')
#hasNewVersionCode=$(git diff build.gradle | egrep '^\+' | grep versionCode | grep -i ${brand})
hasNewVersionCode=$(git diff build.gradle | egrep '^\+' | grep versionCode)

echo "hasNewVersionCode: $hasNewVersionCode"

# make a small modification in preferences date value so 'Quick Intro' will not play for 1 or 2 days (to allow better PlayStore automated testing)
#if grep -q ${mffile} .gitignore; then
if [[ -z "${hasNewVersionCode}" ]]; then
    echo "Not modifying PreferenceValues.java for build ${mffile}"
else
    todayYYYYMMDD=$(date --date='1 day' +%Y-%m-%d)
    if [[ -n "$(grep 'NO_SHOWCASE_FOR_VERSION_BEFORE ='  ./src/com/doubleyellow/scoreboard/prefs/PreferenceValues.java | grep -v ${todayYYYYMMDD})" ]]; then
        echo "Adapting NO_SHOWCASE_FOR_VERSION_BEFORE to $todayYYYYMMDD in PreferenceValues.java"
        sed -i "s~\(NO_SHOWCASE_FOR_VERSION_BEFORE\s*=\s*.\)[0-9-]*~\1${todayYYYYMMDD}~" ./src/com/doubleyellow/scoreboard/prefs/PreferenceValues.java
        #echo "TEMPORARY NOT CHANGING DATE TO TEST CASTING AND CHRONOS"
    fi
fi

if [[ -z "${hasNewVersionCode}" ]]; then
    if [[ -n "$(grep versionCode build.gradle | grep -i ${brand})" ]]; then
        echo "Specify new version code for ${brand}"
        read -p "Open build.gradle [Y/n] ?" ANWSER
        if [[ "${ANWSER:-y}" = "y" ]]; then
            vi +/${brand} build.gradle
            exit 1
        fi
    else
        read -t 10 -p "Warning : continue without changing version code for ${brand} ?"
    fi
fi

## will be repopulated by ./gradlew
#/bin/rm -rf -v .gradle
## will be repopulated during build
#/bin/rm -rf -v build
#/bin/rm -rfv $HOME/.android/build-cache

if [[ 1 -eq 2 ]]; then
    echo "TEMP EXIT"
    exit 1
fi

targetdir=/osshare/code/gitlab/double-yellow.be/app

iStep=${2:-1}
echo "Changed files : $changedFiles"

if [[ -n "${changedFiles}" ]]; then
    echo "*** There were changes. ${changedFiles}"
    echo "*** Rebuilding..."
    if [[ ${iStep} -gt 2 ]]; then
        iStep=1
    fi
else
    echo "*** There were no changes. Not rebuilding..."
    if [[ ${iStep} -eq 1 ]]; then
        iStep=2
    fi
fi

if [[ ${iStep} -le 1 ]]; then
    echo "Cleaning ... ${pkg}"
    ./gradlew clean

    echo "Building ... ${pkg}"
    if ./gradlew assemble; then
        productFlavors="phoneTabletPre22 phoneTabletPost23 wearOs"
        for productFlavor in ${productFlavors}; do

            relapk=$(find . -name "*-${productFlavor}-release.apk")
            dbgapk=$(find . -name "*-${productFlavor}-debug.apk")

            # determine correct version number from manifest
            mergedManifest=$(find build/intermediates/merged_manifests/${productFlavor}Release -name AndroidManifest.xml)
            versionCode=$(head ${mergedManifest} | grep versionCode | sed -e 's~[^0-9]~~g')

            if [[ -e ${relapk} ]]; then
                cp -v -p --backup ${relapk} ${targetdir}/Score-${brand}.${productFlavor}-${versionCode}.apk
            else
                echo "No release file. Maybe because no signingconfig?!"
                cp -v -p --backup ${dbgapk} ${targetdir}/Score-${brand}.${productFlavor}-${versionCode}.DEBUG_NO_RELEASE.apk
            fi

            #read -p "Does copy look ok"
            if [[ 1 -eq 2 ]]; then
                if [[ -n "${relapk}" ]]; then
                    ls -l ${relapk}
                    echo "adb -s \${device} install -r Squore/${relapk}"
                fi
                if [[ -n "${dbgapk}" ]]; then
                    ls -l ${dbgapk}
                    echo "adb -s \${device} install -r Squore/${dbgapk}"
                fi
            fi

        done
    else
        echo '#################### Building failed #####################' > /dev/stderr
        exit 1
    fi
fi
#set -x
if [[ ${iStep} -le 2 ]]; then
    devices="$(adb devices | egrep -v '(List of|^$)' | sed 's~ *device~~')"
    for dvc in ${devices}; do
        #set -x
        build_version_sdk=$(adb -s ${dvc} shell getprop ro.build.version.sdk | sed -e 's~[^0-9]~~')
        build_product_model=$(adb -s ${dvc} shell getprop ro.product.model)
        build_characteristics=$(adb -s ${dvc} shell getprop ro.build.characteristics) # "emulator,nosdcard,watch",default

        productFlavor="phoneTabletPost23"
        if [[ ${build_version_sdk} -lt 23 ]]; then
            productFlavor="phoneTabletPre22"
        fi
        #if [[ "${build_product_model}" =~ "wear" ]]; then
        #    productFlavor="wearOs"
        #fi
        if [[ "${build_characteristics}" =~ "watch" ]]; then
            productFlavor="wearOs"
        fi
        mergedManifest=$(find build/intermediates/merged_manifests/${productFlavor}Release -name AndroidManifest.xml)
        versionCode=$(head ${mergedManifest} | grep versionCode | sed -e 's~[^0-9]~~g')

        set +x
        echo "Installing new ${productFlavor} version on device ${dvc}..."
        apkFile=${targetdir}/Score-${brand}.${productFlavor}-${versionCode}.apk

echo "[TMP] Uninstalling previous version of ${pkg} ..."
adb -s ${dvc} uninstall ${pkg}

        adb -s ${dvc} install -r ${apkFile} 2> tmp.adb.install # 1> /dev/null
        if grep failed tmp.adb.install; then
            echo "Uninstalling previous version of ${pkg} to install new version ..."
            # uninstall previous app
            adb -s ${dvc} uninstall ${pkg}

            echo 'Installing new version (after uninstall) ...'
            adb -s ${dvc} install -r ${apkFile} 2> tmp.adb.install
        fi

        # launch the app
        echo "Launching the app ${pkg} ..."
        #set -x
        adb -s ${dvc} shell monkey -p ${pkg} -c android.intent.category.LAUNCHER 1 > /dev/null 2> /dev/null
        set +x
        # adb -s ${dvc} logcat
        echo "adb -s ${dvc} logcat | egrep '(SB|doubleyellow)' | egrep -v '(AutoResize)'"
    done

    if [[ -z "${devices}" ]]; then
        echo "############### No devices found to install the app..."
    fi

    rm tmp.adb.install 2> /dev/null
fi
# install a shortcut
#    pkg=com.doubleyellow.scoreboard
#
#    adb -d shell am broadcast \
#    -a com.android.launcher.action.INSTALL_SHORTCUT \
#    --es Intent.EXTRA_SHORTCUT_NAME "Squore" \
#    --esn Intent.EXTRA_SHORTCUT_ICON_RESOURCE \
#    ${pkg}/.activity
#
