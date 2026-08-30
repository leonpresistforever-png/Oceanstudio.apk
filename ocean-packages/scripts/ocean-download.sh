#!/usr/bin/env bash

# Checksum-addressed, resumable downloader used by the pinned Android package
# builder. A recipe may provide verified fallback URLs separated by `|`; every
# candidate is accepted only after matching the recipe's SHA-256.
termux_download() {
	if [[ $# != 2 && $# != 3 ]]; then
		echo "termux_download(): expected <URL[|MIRROR...]> <DESTINATION> [<SHA256>]" >&2
		return 1
	fi
	local url_spec="$1" destination="$2" checksum="${3:-SKIP_CHECKSUM}"
	local cache_root="${OCEAN_SOURCE_CACHE:-$TERMUX_SCRIPTDIR/.ocean-cache/sources}"
	local cache_file="" partial_file
	mkdir -p "$cache_root"

	verify() {
		[[ -f "$1" ]] || return 1
		[[ "$checksum" == "SKIP_CHECKSUM" ]] && return 0
		[[ -n "$checksum" ]] || return 1
		[[ "$(sha256sum "$1" | cut -d' ' -f1)" == "$checksum" ]]
	}

	if verify "$destination"; then return 0; fi
	rm -f "$destination"
	if [[ "$checksum" != "SKIP_CHECKSUM" && -n "$checksum" ]]; then
		cache_file="$cache_root/$checksum"
		if verify "$cache_file"; then
			cp -f "$cache_file" "$destination"
			return 0
		fi
		rm -f "$cache_file"
	fi

	IFS='|' read -r -a urls <<< "$url_spec"
	for url in "${urls[@]}"; do
		if [[ "$url" =~ ^file://(/[^/]+)+$ ]]; then
			local source="${url:7}"
			[[ -f "$source" ]] || continue
			cp -f "$source" "$destination"
			verify "$destination" || { rm -f "$destination"; continue; }
		else
			partial_file="$cache_root/.partial-${checksum:-$(printf %s "$url" | sha256sum | cut -d' ' -f1)}"
			echo "Downloading $url"
			# curl retries transient HTTP failures (including 429/5xx), connection
			# resets, refused connections, and timeouts with exponential backoff.
			# A stable partial file allows safe byte-range resume across attempts.
			if ! curl --fail --location --continue-at - \
				--retry 10 --retry-all-errors --retry-connrefused \
				--retry-max-time 900 \
				--connect-timeout 30 --max-time 1200 \
				--speed-limit 1024 --speed-time 90 \
				--output "$partial_file" "$url"; then
				echo "Source candidate failed: $url" >&2
				continue
			fi
			if ! verify "$partial_file"; then
				echo "Checksum rejected source candidate: $url" >&2
				rm -f "$partial_file"
				continue
			fi
			mv "$partial_file" "$destination"
		fi

		if verify "$destination"; then
			[[ -z "$cache_file" ]] || cp -f "$destination" "$cache_file"
			return 0
		fi
	done
	echo "All verified source candidates failed: $url_spec" >&2
	return 1
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then termux_download "$@"; fi
