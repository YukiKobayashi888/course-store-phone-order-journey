#!/usr/bin/env sh
set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes_dir="$repo_dir/build/classes"
mkdir -p "$classes_dir"
find "$repo_dir/src/main/java" -name '*.java' -print | sort | xargs javac -d "$classes_dir"
java -cp "$classes_dir" learning.storefront.OrderLearningDemo "${1:-+15551234567}"
