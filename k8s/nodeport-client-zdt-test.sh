#!/bin/bash

for ((i=1; i<=100000; i++)); do
  curl "http://localhost:32015/api/hello"
  printf "\n"
done