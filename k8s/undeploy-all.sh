#!/bin/bash

script_home=$(dirname "$0")

kubectl delete -f "${script_home}"/app/app-service.yaml
kubectl delete deployment zdt-app -n zdt-test
kubectl delete namespace zdt-test