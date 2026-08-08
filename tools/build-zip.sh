#!/usr/bin/env bash

set -e

# Optional git tag?
if [ $# -ge 1 ]; then
	git_tag="$1"
	shift
fi

saved_pwd=$PWD

# Check we are within a git repo
git_dir=$( git rev-parse --show-toplevel )
if [ -z "${git_dir}" ]; then
	echo "Cannot determine top-level directory for git repo"
	exit 1
fi

# Change to git top-level
cd ${git_dir}

# Check we are in 'master' branch
# branch_name=$( git symbolic-ref -q HEAD ) || echo "Cannot determine branch name" && exit 1
# branch_name=${branch_name##refs/heads/}
# echo "Current git branch: ${branch_name}"
# if [ "${branch_name}" != "master" ]; then
# 	echo "Unexpected current branch '${branch_name}' - expecting 'master'"
# 	exit 1
# fi

# Determine project name
project=$( perl -n -e 'if (m/<artifactId>(\w+)<.artifactId>/) { print $1; exit }' pom.xml $)
if [ -z "${project}" ]; then
	echo "Unable to determine project name from pom.xml?"
	exit 1
fi

# Extract git tag
if [ -z "${git_tag}" ]; then
	git_tag=$( git tag --points-at HEAD )
	if [ -z "${git_tag}" ]; then
		echo "Unable to extract git tag"
		exit 1
	fi
fi

build_dir=/tmp/${project}
commit_ts=$( git show --no-patch --format=%cI )

/bin/rm -fr ${build_dir}
mkdir -p ${build_dir}

cp target/${project}*.jar ${build_dir}/${project}.jar

git show HEAD:log4j2.properties > ${build_dir}/log4j2.properties

git show HEAD:start.sh > ${build_dir}/start.sh
git show HEAD:stop.sh > ${build_dir}/stop.sh

chmod +x ${build_dir}/start.sh
chmod +x ${build_dir}/stop.sh

printf "{\n}\n" > ${build_dir}/settings.json

gtouch -d ${commit_ts%%+??:??} ${build_dir} ${build_dir}/*

rm -f ${saved_pwd}/${project}.zip
(cd ${build_dir}/..; 7z a -r -tzip ${saved_pwd}/${project}.zip ${project}/)
