#!/bin/sh

NC='\033[0m' # No Color
RED='\033[0;31m'
GREEN='\033[0;32m'

# Track the pid if we can find it
read pid 2>/dev/null <run.pid
valid_pid=$?

echo 'Calling GET /admin/stop on local Qortal node'
if curl --url http://localhost:12391/admin/stop 1>/dev/null 2>&1; then
        echo "Qortal node responded and should be shutting down"
        if [ $valid_pid -eq 0 ]; then
                echo "Monitoring for Qortal to end"
                tail --pid=${pid} -f /dev/null
                rm run.pid
                echo "${GREEN}Qortal ended gracefully"
                echo "${NC}"
        fi
        exit 0
else
        echo "${RED}No response from Qortal node - not running?"
        echo "${NC}"
        exit 1
fi
