#!/usr/bin/env sh
set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes_dir="$repo_dir/build/classes"
rm -rf "$classes_dir"
mkdir -p "$classes_dir"
find "$repo_dir/src/main/java" "$repo_dir/src/test/java" -name '*.java' -print \
  | sort \
  | xargs javac -d "$classes_dir"
java -ea -cp "$classes_dir" learning.storefront.orders.CourseOrderJourneyTest
