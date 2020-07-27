#!/bin/bash

script_home=$(dirname "$0")
kubectl create namespace zdt-test
kubectl apply -f "${script_home}"/app/app-service.yaml
kubectl apply -f "${script_home}"/app/app-v1-deployment.yaml
printf "Waiting for pods to run..."
sleep 15
printf "\n**********************************************************************************************************\n"
kubectl get svc,po -n zdt-test