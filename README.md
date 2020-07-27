# 1. Zero Downtime Rolling Update
A demo to Zero Down Time Rolling update of deployments in Kubernetes.

Note: These below steps are for `Kubernetes 1.16.5` on `docker-desktop`. 

# 2. Build Project
From the project's home directory execute `mvn clean install` to generate executable jars.

# 3. Build Docker Image for app
Execute `docker build --no-cache -t zdt-app ./target/`

```shell script
$ docker build --no-cache -t zdt-app ./target/
.....
.....
.....
Successfully built ce5fd1132cbb
Successfully tagged zdt-app:latest
```

# 4. Deploy App V1
Deploy first app with version `v1.0.0` with service type `NodePort`. Execute `sh k8s/deploy-all.sh`. 

```shell script
$ sh k8s/deploy-all.sh 
namespace/zdt-test created
service/zdt-service created
deployment.apps/zdt-app created
Waiting for pods to run...
**********************************************************************************************************
NAME                  TYPE       CLUSTER-IP     EXTERNAL-IP   PORT(S)          AGE
service/zdt-service   NodePort   10.110.55.33   <none>        9015:32015/TCP   15s

NAME                           READY   STATUS    RESTARTS   AGE
pod/zdt-app-647588dfc7-hz97j   1/1     Running   0          15s
pod/zdt-app-647588dfc7-r7hkm   1/1     Running   0          15s
pod/zdt-app-647588dfc7-zv9w9   1/1     Running   0          15s
```

# 5. Start one or more clients from two different shells
Execute `sh k8s/nodeport-client-zdt-test.sh`

```shell script
$ sh k8s/nodeport-client-zdt-test.sh 
 {"App Version":"v1.0.0","Request Count":210,"Container Name":"zdt-app-647588dfc7-zv9w9"}
 {"App Version":"v1.0.0","Request Count":171,"Container Name":"zdt-app-647588dfc7-r7hkm"}
 {"App Version":"v1.0.0","Request Count":232,"Container Name":"zdt-app-647588dfc7-hz97j"}
```

# 6. Perform Rolling Update deployment to app/v2

The deployment `app/v2` contains `preStop` hook. 

```yaml
  spec:
    containers:
      lifecycle:
        preStop:
          exec:
            command: ["sleep","10"]
      readinessProbe:
```

```shell script
$ kubectl apply -f k8s/app/hook-app-v2-deployment.yaml
  deployment.apps/zdt-app configured
```

You would notice below errors and seconds of pause in both clients. NodePort client returns `(52) Empty reply from server`.
This is because `app/v1` deployment does not have `preStop` hook and few requests still go to the containers of `app/v1` 
while they are terminating. 

```shell script
{"App Version":"v1.0.0","Request Count":10585,"Container Name":"zdt-app-647588dfc7-zv9w9"}
curl: (52) Empty reply from server

curl: (52) Empty reply from server

{"App Version":"v2.0.0-preStop","Request Count":439,"Container Name":"zdt-app-c9dd5466-nb79r"}
{"App Version":"v2.0.0-preStop","Request Count":441,"Container Name":"zdt-app-c9dd5466-nb79r"}
{"App Version":"v2.0.0-preStop","Request Count":442,"Container Name":"zdt-app-c9dd5466-nb79r"}
curl: (52) Empty reply from server

{"App Version":"v2.0.0-preStop","Request Count":444,"Container Name":"zdt-app-c9dd5466-nb79r"}
{"App Version":"v1.0.0","Request Count":10428,"Container Name":"zdt-app-647588dfc7-hz97j"}
{"App Version":"v1.0.0","Request Count":10429,"Container Name":"zdt-app-647588dfc7-hz97j"}
{"App Version":"v2.0.0-preStop","Request Count":4,"Container Name":"zdt-app-c9dd5466-dhzlm"}
curl: (52) Empty reply from server

curl: (52) Empty reply from server

{"App Version":"v1.0.0","Request Count":10431,"Container Name":"zdt-app-647588dfc7-hz97j"}
curl: (52) Empty reply from server

{"App Version":"v2.0.0-preStop","Request Count":5,"Container Name":"zdt-app-c9dd5466-dhzlm"}
```

# 8. Rolling update when the app is running with preStop hook
For a rolling update with ZDT can be achieved by having a readiness check and most importantly with a preStop hook.
Though a `preStop` hook with a delay of 5-10 seconds can make a smooth transition from current version to new version.

Note: This delay must be less than `terminationGracePeriodSeconds` which is 30 seconds by default.

The last deployed deployment `app/v2` had the `preStop` hook with `delay of 10 seconds`. Now, roll back deployment to 
`app/v1` and notice `no` requests will be failed while terminating `app/v2` and adding back `app/v1`

```shell script
$ kubectl apply -f k8s/app/app-v1-deployment.yaml
  deployment.apps/zdt-app configured
```
 
# 9. Graceful Shutdown
A graceful shutdown of container main process (web server) can improve the robustness if the terminating containers
are serving any requests. The web server in this app is implemented with java runtime shutdown hook 
`Runtime.getRuntime().addShutdownHook` to call the `stopServer()` method. As a result, when container receives termination
signal from Kubernetes the shutdown hook is invoked to give web servers chance to shutdown gracefully.
```
Request Headers: {Connection=[close], User-Agent=[kube-probe/1.16+], Host=[10.1.4.25:9015], Accept-Encoding=[gzip]}
Response: {"App Version":"v1.0.0","Request Count":1975,"Container Name":"zdt-app-647588dfc7-hz97j"}
Stopping the web server...
WEB server is DOWN. Good bye!
```

# 10. Undeploy All
```shell script
$ sh k8s/undeploy-all.sh 
 service "zdt-service" deleted
 deployment.apps "zdt-app" deleted
 namespace "zdt-test" deleted
```