#!/usr/bin/env sh
# shellcheck shell=ash

set -eu

download_additional_jars() {
  set -eu

  local url sha256 file
  mkdir -p -- "$2"
  file="${3%/*}"
  mkdir -p -- "$file"

  while read -r url sha256 file; do
    [ -n "$file" ] || file="${url##*/}"
    if [ -z "$url" ] || [ -z "$sha256" ] || [ -z "$file" ] || [ "$file" = "$url" ]; then
      echo failed to parse line : "$url $sha256 $file" 1>&2
      exit 1
    fi

    file="$2/$file"

    { [ -e "$file" ] && printf '%s *%s' "$sha256" "$file" | sha256sum -c; } || {
      [ ! -e "$file" ] || {
        echo path obstructed: "$file" 1>&2
        exit 1
      }

      { wget -O "$file" "$url" && printf '%s *%s' "$sha256" "$file" | sha256sum -c; } || {
        rm -f -- "$file"
        exit 1
      }
    }

    printf '%s:' "$file" >>"$3"
  done <"$1"
}

produce_classpath() {
  cp=

  split_searchpath() {
    local paths path
    paths="$2"

    set -- "$1"

    while [ -n "$paths" ]; do
      path="${paths%%:*}"
      paths="${paths#$path:}"
      [ "$paths" != "$path" ] || paths=''
      [ -z "$path" ] || set -- "$@" "$path"
    done

    [ $# -le 1 ] || "$@"
  }

  add_path_to_cp() {
    while [ $# -gt 0 ]; do
      [ -e "$1" ] || {
        echo "path doesn't exist: $1" 1>&2
        exit 1
      }
      if [ -z "$cp" ]; then
        cp="$1"
      else
        cp="$cp:$1"
      fi
      shift
    done
  }

  add_files_to_cp() {
    local fileContents
    while [ $# -gt 0 ]; do
      fileContents="$(cat -- "$1")"
      split_searchpath add_path_to_cp "$fileContents"
      shift
    done
  }

  set -- /app/jib-classpath-file
  [ ! -f /app/downloads/classpath ] || set -- "$1:/app/downloads/classpath"
  [ -z "${ADDITIONAL_CLASSPATH_FILES-}" ] || set -- "$1:$ADDITIONAL_CLASSPATH_FILES"

  split_searchpath add_files_to_cp "$@"
}

run_app() {
  produce_classpath
  set -- -cp "$cp" @/app/jib-main-class-file "$@"

  [ -z "${JAVA_OPTS-}" ] || {
    # shellcheck disable=SC2086
    set -- $JAVA_OPTS "$@"
  }

  exec java "$@"
}

case "${1-}" in
download)
  shift && download_additional_jars \
    "${1-/app/download-list}" \
    "${2-/app/downloads/files}" \
    "${3-/app/downloads/classpath}"
  exit
  ;;
--) shift ;;
esac

run_app "$@"
