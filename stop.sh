#!/usr/bin/env bash

# Check for color support
if [ -t 1 ]; then
	ncolors=$( tput colors )
	if [ -n "${ncolors}" -a "${ncolors}" -ge 8 ]; then
		if normal="$( tput sgr0 )"; then
			# use terminfo names
			red="$( tput setaf 1 )"
			green="$( tput setaf 2)"
		else
			# use termcap names for FreeBSD compat
			normal="$( tput me )"
			red="$( tput AF 1 )"
			green="$( tput AF 2)"
		fi
	fi
fi

# Track the pid if we can find it
read pid 2>/dev/null <run.pid
is_pid_valid=$?

# Swap out the API port if the --testnet (or -t) argument is specified
api_port=12391
if [[ "$@" = *"--testnet"* ]] || [[  "$@" = *"-t"* ]]; then
  api_port=62391
fi

# Ensure curl is installed
curl_path=$(which curl)

if [[ -f $curl_path ]]; then

	echo 'Calling GET /admin/stop on local Qortal node'
	if curl --url "http://localhost:${api_port}/admin/stop" 1>/dev/null 2>&1; then
		echo "Qortal node responded and should be shutting down"

		if [ "${is_pid_valid}" -eq 0 ]; then
			echo -n "Monitoring for Qortal node to end"
			while s=`ps -p $pid -o stat=` && [[ "$s" && "$s" != 'Z' ]]; do
				echo -n .
				sleep 1
			done
			echo
			echo "${green}Qortal ended gracefully${normal}"
			rm -f run.pid
		fi
		exit 0
	else
		echo "${red}No response from Qortal node - not running on port ${api_port}?${normal}"
		exit 1
	fi

else
	echo "${red}curl is not installed or in the path${normal}"
	exit 1
fi
