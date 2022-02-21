#!/usr/bin/env bash

start_height=$1
count=$2
target=$3
deviation=$4
power=$5

if [ -z "${start_height}" ]; then
  echo
  echo "Error: missing start height."
  echo
  echo "Usage:"
  echo "block-timings.sh <startheight> [count] [target] [deviation] [power]"
  echo
  echo "startheight: a block height, preferably within the untrimmed range, to avoid data gaps"
  echo "count: the number of blocks to request and analyse after the start height. Default: 100"
  echo "target: the target block time in milliseconds. Originates from blockchain.json. Default: 60000"
  echo "deviation: the allowed block time deviation in milliseconds. Originates from blockchain.json. Default: 30000"
  echo "power: used when transforming key distance to a time offset. Originates from blockchain.json. Default: 0.2"
  echo
  exit
fi

count=${count:=100}
target=${target:=60000}
deviation=${deviation:=30000}
power=${power:=0.2}

finish_height=$((start_height + count - 1))
height=$start_height

echo "Settings:"
echo "Target time offset: ${target}"
echo "Deviation: ${deviation}"
echo "Power transform: ${power}"
echo

function calculate_time_offset {
  local key_distance_ratio=$1
  local transformed=$( echo "" | awk "END {print ${key_distance_ratio} ^ ${power}}")
  local time_offset=$(echo "${deviation}*2*${transformed}" | bc)
  time_offset=${time_offset%.*}
  echo $time_offset
}


function fetch_and_process_blocks {

  echo "Fetching blocks from height ${start_height} to ${finish_height}..."
  echo

  total_time_offset=0
  errors=0

  while [ "${height}" -le "${finish_height}" ]; do
    block_minting_info=$(curl -s "http://localhost:12391/blocks/byheight/${height}/mintinginfo")
    error=$(echo "${block_minting_info}" | jq -r .error)
    if [ "${error}" != "null" ]; then
      echo "Error fetching minting info for block ${height}"
      echo
      errors=$((errors+1))
      height=$((height+1))
      continue;
    fi

    # Parse minting info
    minter_level=$(echo "${block_minting_info}" | jq -r .minterLevel)
    online_accounts_count=$(echo "${block_minting_info}" | jq -r .onlineAccountsCount)
    key_distance_ratio=$(echo "${block_minting_info}" | jq -r .keyDistanceRatio)
    time_delta=$(echo "${block_minting_info}" | jq -r .timeDelta)
    timestamp=$(echo "${block_minting_info}" | jq -r .timestamp)

    time_offset=$(calculate_time_offset "${key_distance_ratio}")
    block_time=$((target-deviation+time_offset))

    echo "=== BLOCK ${height} ==="
    echo "Timestamp: ${timestamp}"
    echo "Minter level: ${minter_level}"
    echo "Online accounts: ${online_accounts_count}"
    echo "Key distance ratio: ${key_distance_ratio}"
    echo "Time offset: ${time_offset}"
    echo "Block time (real): ${time_delta}"
    echo "Block time (calculated): ${block_time}"

    if [ "${time_delta}" -ne "${block_time}" ]; then
      echo "WARNING: Block time mismatch. This is to be expected when using custom settings."
    fi
    echo

    total_time_offset=$((total_time_offset+block_time))

    height=$((height+1))
  done

  adjusted_count=$((count-errors))
  if [ "${adjusted_count}" -eq 0 ]; then
    echo "No blocks were retrieved."
    echo
    exit;
  fi

  mean_time_offset=$((total_time_offset/adjusted_count))
  time_offset_diff=$((mean_time_offset-target))

  echo "==================="
  echo "===== SUMMARY ====="
  echo "==================="
  echo "Total blocks retrieved: ${adjusted_count}"
  echo "Total blocks failed: ${errors}"
  echo "Mean time offset: ${mean_time_offset}ms"
  echo "Target time offset: ${target}ms"
  echo "Difference from target: ${time_offset_diff}ms"
  echo

}

function estimate_key_distance_ratio_for_level {
  local level=$1
  local example_key_distance="0.5"
  echo "(${example_key_distance}/${level})"
}

function estimate_block_timestamps {
  min_block_time=9999999
  max_block_time=0

  echo "===== BLOCK TIME ESTIMATES ====="

  for level in {1..10}; do
    example_key_distance_ratio=$(estimate_key_distance_ratio_for_level "${level}")
    time_offset=$(calculate_time_offset "${example_key_distance_ratio}")
    block_time=$((target-deviation+time_offset))

    if [ "${block_time}" -gt "${max_block_time}" ]; then
      max_block_time=${block_time}
    fi
    if [ "${block_time}" -lt "${min_block_time}" ]; then
      min_block_time=${block_time}
    fi

    echo "Level: ${level}, time offset: ${time_offset}, block time: ${block_time}"
  done
  block_time_range=$((max_block_time-min_block_time))
  echo "Range: ${block_time_range}"
  echo
}

fetch_and_process_blocks
estimate_block_timestamps
