# FourthCafe

# 서비스 시나리오
### 기능적 요구사항
1. 고객이 메뉴를 주문한다.
2. 고객이 결재한다
3. 결재가 완료되면 주문 내역을 보낸다
4. 매장에서 메뉴 완성 후 배달을 시작한다
5. 배송이 완료되면 배송된 메뉴만큼 재고를 입고한다
6. 주문 상태를 고객이 조회 할 수 있다
7. 고객이 주문을 취소 할 수 있다
8. 결재 취소시 배송과 입고도 같이 취소 되어야 한다


### 비기능적 요구사항
1. 트랜젝션
   1. 결재가 취소되면 재고가 입고되지 않는다 → Sync 호출
2. 장애격리
   1. 배송에서 장애가 발생해도 재고는 정상적으로 입고될 수 있어야 한다 → Async(event-driven), Eventual Consistency
   1. 입고가 과중되면 입고를 잠시 후에 하도록 유도한다 → Circuit breaker, fallback
3. 성능
   1. 점원이 재고상태를 재고내역조회에서 확인할 수 있어야 한다 → CQRS

# Event Storming 결과

![EventStormingV1](https://github.com/minksong69/FourthCafe/blob/main/images/eventingstorming_minksong69_fourthcafe2.png)

# 헥사고날 아키텍처 다이어그램 도출
![증빙10](https://github.com/minksong69/FourthCafe/blob/main/images/%ED%97%A5%EC%82%AC%EA%B3%A0%EB%82%A0%20%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98_minksong69_2.png)

# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각각의 포트넘버는 8081 ~ 8085, 8088 이다)
```
cd Order
mvn spring-boot:run  

cd Pay
mvn spring-boot:run

cd Delivery
mvn spring-boot:run 

cd MyPage
mvn spring-boot:run  

cd Inventory
mvn spring-boot:run

cd gateway
mvn spring-boot:run 
```

## DDD 의 적용
msaez.io를 통해 구현한 Aggregate 단위로 Entity를 선언 후, 구현을 진행하였다.

Entity Pattern과 Repository Pattern을 적용하기 위해 Spring Data REST의 RestRepository를 적용하였다.

**Inventory 서비스의 Inventory.java**
```java 
package fourthcafe;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Inventory_table")
public class Inventory {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String ordererName;
    private String menuName;
    private Long menuId;
    private Double price;
    private Integer quantity;
    private String status;

    @PrePersist
    public void onPrePersist(){
        Warehoused warehoused = new Warehoused();
        BeanUtils.copyProperties(this, warehoused);
        warehoused.publishAfterCommit();


        InventoryCanceled inventoryCanceled = new InventoryCanceled();
        BeanUtils.copyProperties(this, inventoryCanceled);
        inventoryCanceled.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrdererName() {
        return ordererName;
    }

    public void setOrdererName(String ordererName) {
        this.ordererName = ordererName;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }
}
```

**Inventory 서비스의 PolicyHandler.java**
```java
package fourthcafe;

import fourthcafe.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{

    @Autowired
    InventoryRepository inventoryRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    
}
```

DDD 적용 후 REST API의 테스트를 통하여 정상적으로 동작하는 것을 확인할 수 있었다.

- 원격 주문 (Inventory 주문 후 결과)

![증빙2](https://github.com/minksong69/FourthCafe/blob/main/images/Inventory%20%EC%A3%BC%EB%AC%B8%20%ED%9B%84%20%EA%B2%B0%EA%B3%BC.png)

# GateWay 적용
API GateWay를 통하여 마이크로 서비스들의 집입점을 통일할 수 있다. 다음과 같이 GateWay를 적용하였다.

```yaml
server:
  port: 8088

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: Order
          uri: http://localhost:8081
          predicates:
            - Path=/orders/** 
        - id: Pay
          uri: http://localhost:8082
          predicates:
            - Path=/pays/** 
        - id: Delivery
          uri: http://localhost:8083
          predicates:
            - Path=/deliveries/** 
        - id: MyPage
          uri: http://localhost:8084
          predicates:
            - Path= /myPages/**
        - id: Inventory
          uri: http://localhost:8085
          predicates:
            - Path=/inventories/** 
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true


---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: Order
          uri: http://Order:8080
          predicates:
            - Path=/orders/** 
        - id: Pay
          uri: http://Pay:8080
          predicates:
            - Path=/pays/** 
        - id: Delivery
          uri: http://Delivery:8080
          predicates:
            - Path=/deliveries/** 
        - id: MyPage
          uri: http://MyPage:8080
          predicates:
            - Path= /myPages/**
        - id: Inventory
          uri: http://Inventory:8080
          predicates:
            - Path=/inventories/** 
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080
```
8088 port로 Inventory서비스 정상 호출

![증빙1](https://github.com/minksong69/FourthCafe/blob/main/images/Inventory%EC%84%9C%EB%B9%84%EC%8A%A4%20%EC%A0%95%EC%83%81%20%ED%98%B8%EC%B6%9C.png)

# CQRS/saga/correlation
Materialized View를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이)도 내 서비스의 화면 구성과 잦은 조회가 가능하게 구현해 두었다. 본 프로젝트에서 View 역할은 MyPages 서비스가 수행한다.

주문(ordered) 실행 후 MyPages 화면

![증빙3](https://github.com/minksong69/FourthCafe/blob/main/images/%EC%A3%BC%EB%AC%B8%20%ED%9B%84%20myPages2.png)

주문(OrderCancelled) 취소 후 MyPages 화면

![증빙4](https://github.com/minksong69/FourthCafe/blob/main/images/%EC%A3%BC%EB%AC%B8%20%EC%B7%A8%EC%86%8C%20%ED%9B%84%20myPages.png)

위와 같이 주문을 하게되면 Order > Pay > Delivery > Inventory > MyPage로 주문이 Assigned 되고

주문 취소가 되면 Status가 deliveryCancelled로 Update 되는 것을 볼 수 있다.

또한 Correlation을 Key를 활용하여 Id를 Key값을 하고 원하는 주문하고 서비스간의 공유가 이루어 졌다.

위 결과로 서로 다른 마이크로 서비스 간에 트랜잭션이 묶여 있음을 알 수 있다.

# 폴리글랏
Order 서비스의 DB와 MyPage의 DB를 다른 DB를 사용하여 폴리글랏을 만족시키고 있다.

**Inventory의 pom.xml DB 설정 코드**

![증빙5](https://github.com/minksong69/FourthCafe/blob/main/images/Inventory%20DB%20%EC%84%A4%EC%A0%95.png)

**MyPage의 pom.xml DB 설정 코드**

![증빙6](https://github.com/minksong69/FourthCafe/blob/main/images/MyPage%20DB%20%EC%84%A4%EC%A0%95.png)

# 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 배송(Delivery)와 재고(Inventory) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 Rest Repository에 의해 노출되어있는 REST 서비스를 FeignClient를 이용하여 호출하도록 한다.

**Delivery 서비스 내 external.InventoryService**
```java
package forthcafe.external;

import org.springframework.cloud.openfeign.FeignClient; 
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="Inventory", url="${api.url.inventory}") 
public interface InventoryService {

    @RequestMapping(method = RequestMethod.POST, path = "/inventories", consumes = "application/json")
    public void delivery(@RequestBody Inventory inventory);

}
```

**동작 확인**

잠시 Inventory 서비스 중지
![증빙7](https://github.com/minksong69/FourthCafe/blob/main/images/Inventory%20%EC%9A%B4%EC%98%81%20%EC%A4%91%EC%A7%80.png)

주문 요청시 오류 발생
![증빙8](https://github.com/minksong69/FourthCafe/blob/main/images/%EC%A3%BC%EB%AC%B8%20%EC%9A%94%EC%B2%AD%EC%8B%9C%20%EC%98%A4%EB%A5%98.png)

Inventory 서비스 재기동
![증빙9](https://github.com/minksong69/FourthCafe/blob/main/images/Inventory%20%EC%9E%AC%EA%B8%B0%EB%8F%99.png)

주문 요청이 정상적으로 처리됨
![증빙9](https://github.com/minksong69/FourthCafe/blob/main/images/%EC%9E%AC%EA%B8%B0%EB%8F%99%20%ED%9B%84%20%EC%A0%95%EC%83%81%20%EC%B2%98%EB%A6%AC.png)
![증빙10](https://github.com/minksong69/FourthCafe/blob/main/images/%EC%9E%AC%EA%B8%B0%EB%8F%99%20%ED%9B%84%20%EC%A0%95%EC%83%81%20%EC%B2%98%EB%A6%AC2.png)

Fallback 설정
![image](https://user-images.githubusercontent.com/78134028/110066852-c144db80-7db5-11eb-88a3-c1b59357e7ff.png)
![image](https://github.com/minksong69/FourthCafe/blob/main/images/Fallback%20%EC%84%A4%EC%A0%952.png)


Fallback 결과(Inventory 서비스 종료 후 Order 추가 시)
![image](https://user-images.githubusercontent.com/78134028/110067047-2b5d8080-7db6-11eb-9504-a67fd86f3903.png)
![image](https://user-images.githubusercontent.com/78134028/110067140-665fb400-7db6-11eb-81df-6f98939d5d32.png)


## 서킷 브레이킹
* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함
* Delivery -> Inventory 와의 Req/Res 연결에서 요청이 과도한 경우 CirCuit Breaker 통한 격리
* Hystrix 를 설정: 요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정

```
// Deliery서비스 application.yml

feign:
  hystrix:
    enabled: true

hystrix:
  command:
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610
```


```
// Inventory 서비스 Inventory.java

 @PrePersist
    public void onPrePersist(){
        Warehoused warehoused = new Warehoused();
        BeanUtils.copyProperties(this, warehoused);
        warehoused.publishAfterCommit();

        try {
            Thread.currentThread().sleep((long) (400 + Math.random() * 220));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
```

* /home/project/team/forthcafe/yaml/siege.yaml
```
apiVersion: v1
kind: Pod
metadata:
  name: siege
spec:
  containers:
  - name: siege
    image: apexacme/siege-nginx
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인: 동시사용자 5명 10초 동안 실시
```
siege -c5 -t10S  -v --content-type "application/json" 'http://localhost:8081/orders POST {"memuId":2, "quantity":1}'
```
![image](https://user-images.githubusercontent.com/5147735/109762408-dd207400-7c33-11eb-8464-325d781867ae.png)
![image](https://user-images.githubusercontent.com/5147735/109762376-d1cd4880-7c33-11eb-87fb-b739aa2d6621.png)

# 운영

## CI/CD
* 카프카 설치
```
- 헬름 설치
참고 : http://msaschool.io/operation/implementation/implementation-seven/
curl https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 > get_helm.sh
chmod 700 get_helm.sh
./get_helm.sh

- Azure Only
kubectl patch storageclass managed -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'

- 카프카 설치
kubectl --namespace kube-system create sa tiller      # helm 의 설치관리자를 위한 시스템 사용자 생성
kubectl create clusterrolebinding tiller --clusterrole cluster-admin --serviceaccount=kube-system:tiller

helm repo add incubator https://charts.helm.sh/incubator
helm repo update
kubectl create ns kafka
helm install my-kafka --namespace kafka incubator/kafka

kubectl get po -n kafka -o wide
```
* Topic 생성
```
kubectl -n kafka exec my-kafka-0 -- /usr/bin/kafka-topics --zookeeper my-kafka-zookeeper:2181 --topic forthcafe --create --partitions 1 --replication-factor 1
```
* Topic 확인
```
kubectl -n kafka exec my-kafka-0 -- /usr/bin/kafka-topics --zookeeper my-kafka-zookeeper:2181 --list
```
* 이벤트 발행하기
```
kubectl -n kafka exec -ti my-kafka-0 -- /usr/bin/kafka-console-producer --broker-list my-kafka:9092 --topic forthcafe
```
* 이벤트 수신하기
```
kubectl -n kafka exec -ti my-kafka-0 -- /usr/bin/kafka-console-consumer --bootstrap-server my-kafka:9092 --topic forthcafe --from-beginning
```

* 소스 가져오기
```
git clone https://github.com/minksong69/FourthCafe.git
```

* deployment.yml 파일 image버전 초기 설정으로 변경(-> v1), 총 5개 --> 맨 앞의 이름도 변경(skteam01 --> skuser11)

/FourthCafe-main/Delivery/kubernetes/deployment.yml

/FourthCafe-main/MyPage/kubernetes/deployment.yml

/FourthCafe-main/Order/kubernetes/deployment.yml

/FourthCafe-main/Pay/kubernetes/deployment.yml

/FourthCafe-main/Inventory/kubernetes/deployment.yml


## ConfigMap
* deployment.yml 파일에 설정
```
env:
   - name: SYS_MODE
     valueFrom:
       configMapKeyRef:
         name: systemmode
         key: sysmode
```
* Configmap 생성, 정보 확인
```
kubectl create configmap systemmode --from-literal=sysmode=PRODUCT
kubectl get configmap systemmode -o yaml
```
![image](https://user-images.githubusercontent.com/78134028/110075933-fefe3000-7dc6-11eb-9806-9d63f124f13b.png)


* order 1건 추가후 로그 확인
```
kubectl logs {pod ID}
```
![image](https://user-images.githubusercontent.com/78134028/110077686-ec392a80-7dc9-11eb-99ab-550172ffd732.png)



## Deploy / Pipeline

* build 하기
```
cd /FourthCafe-main

cd Order
mvn package 

cd ..
cd Pay
mvn package

cd ..
cd Delivery
mvn package

cd ..
cd gateway
mvn package

cd ..
cd MyPage
mvn package

cd ..
cd Inventory
mvn package
```

* Azure 레지스트리에 도커 이미지 push, deploy, 서비스생성(방법1 : yml파일 이용한 deploy)
```
cd .. 
cd Order
az acr build --registry skuser11 --image skuser11.azurecr.io/order:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl expose deploy order --type=ClusterIP --port=8080

cd .. 
cd Pay
az acr build --registry skuser11 --image skuser11.azurecr.io/pay:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl expose deploy pay --type=ClusterIP --port=8080

cd .. 
cd Delivery
az acr build --registry skuser11 --image skuser11.azurecr.io/delivery:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl expose deploy delivery --type=ClusterIP --port=8080


cd .. 
cd MyPage
az acr build --registry skuser11 --image skuser11.azurecr.io/mypage:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl expose deploy mypage --type=ClusterIP --port=8080

cd .. 
cd gateway
az acr build --registry skuser11 --image skuser11.azurecr.io/gateway:v1 .
kubectl create deploy gateway --image=skuser11.azurecr.io/gateway:v1
kubectl expose deploy gateway --type=LoadBalancer --port=8080

cd .. 
cd Inventory
az acr build --registry skuser11 --image skuser11.azurecr.io/inventory:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl expose deploy inventory --type=ClusterIP --port=8080
```


* Azure 레지스트리에 도커 이미지 push, deploy, 서비스생성(방법2)
```
cd ..
cd Order
az acr build --registry skuser11 --image skuser11.azurecr.io/order:v1 .
kubectl create deploy order --image=skuser11.azurecr.io/order:v1
kubectl expose deploy order --type=ClusterIP --port=8080

cd .. 
cd Pay
az acr build --registry skuser11 --image skuser11.azurecr.io/pay:v1 .
kubectl create deploy pay --image=skuser11.azurecr.io/pay:v1
kubectl expose deploy pay --type=ClusterIP --port=8080


cd .. 
cd Delivery
az acr build --registry skuser11 --image skuser11.azurecr.io/delivery:v1 .
kubectl create deploy delivery --image=skuser11.azurecr.io/delivery:v1
kubectl expose deploy delivery --type=ClusterIP --port=8080


cd .. 
cd gateway
az acr build --registry skuser11 --image skuser11.azurecr.io/gateway:v1 .
kubectl create deploy gateway --image=skuser11.azurecr.io/gateway:v1
kubectl expose deploy gateway --type=LoadBalancer --port=8080

cd .. 
cd MyPage
az acr build --registry skuser11 --image skuser11.azurecr.io/mypage:v1 .
kubectl create deploy mypage --image=skuser11.azurecr.io/mypage:v1
kubectl expose deploy mypage --type=ClusterIP --port=8080

cd .. 
cd Inventory
az acr build --registry skuser11 --image skuser11.azurecr.io/inventory:v1 .
kubectl create deploy inventory --image=skuser11.azurecr.io/mypage:v1
kubectl expose deploy inventory --type=ClusterIP --port=8080

kubectl logs {pod명}
```
* Service, Pod, Deploy 상태 확인
![image](https://user-images.githubusercontent.com/78134028/110071497-821b8800-7dbf-11eb-93d2-1687c0520ef1.png)



* deployment.yml  참고
```
1. image 설정
2. env 설정 (config Map) 
3. readiness 설정 (무정지 배포)
4. liveness 설정 (self-healing)
5. resource 설정 (autoscaling)
```

```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: inventory
  labels:
    app: inventory
spec:
  replicas: 1
  selector:
    matchLabels:
      app: inventory
  template:
    metadata:
      labels:
        app: inventory
    spec:
      containers:
        - name: inventory
          image: skuser11.azurecr.io/inventory:v1
          ports:
            - containerPort: 8080
          # autoscale start
          resources:
              limits:
                cpu: 500m
              requests:
                cpu: 200m
          # autoscale end
          ### config map start
          #env:
          #  - name: SYS_MODE
          #    valueFrom:
          #      configMapKeyRef:
          #        name: systemmode
          #        key: sysmode
          ### config map end
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
            
```



## 오토스케일 아웃
* 앞서 서킷 브레이커(CB) 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다.

* inventory 서비스 deployment.yml 설정
```
 resources:
            limits:
              cpu: 500m
            requests:
              cpu: 200m
```
* 다시 배포해준다.
```
/home/project/personal/FourthCafe-main/Order/mvn package
az acr build --registry skuser11 --image skuser11.azurecr.io/inventory:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl expose deploy inventory --type=ClusterIP --port=8080
```

* Inventory 서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다

```
kubectl autoscale deploy inventory --min=1 --max=10 --cpu-percent=15
```

* /home/project/personal/FourthCafe-main/yaml/siege.yaml
```
apiVersion: v1
kind: Pod
metadata:
  name: siege
spec:
  containers:
  - name: siege
    image: apexacme/siege-nginx
```

* siege pod 생성
```
/home/project/personal/FourthCafe-main/yaml/kubectl apply -f siege.yaml
```

* siege를 활용해서 워크로드를 1000명, 1분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege -c1000 -t60S  -v --content-type "application/json" 'http://{EXTERNAL-IP}:8080/inventories POST {"memuId":2, "quantity":1}'
```

* 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다
```
kubectl get deploy order -w
```
![image](https://user-images.githubusercontent.com/78134028/110072596-60bb9b80-7dc1-11eb-955f-a5a34c024614.png)

```
kubectl get pod
```
![image](https://user-images.githubusercontent.com/78134028/110072920-e4758800-7dc1-11eb-9085-5633e44c6eb3.png)





## 무정지 재배포 (Readiness Probe)
* 배포전

![image](https://user-images.githubusercontent.com/78134028/110078939-c876e400-7dcb-11eb-97a7-505f69c1710e.png)



* 배포중

![image](https://user-images.githubusercontent.com/78134028/110079024-e5131c00-7dcb-11eb-9405-664cc8c6a98c.png)

![image](https://user-images.githubusercontent.com/78134028/110079153-1ab80500-7dcc-11eb-9444-405c7c66b836.png)



* 배포후

![image](https://user-images.githubusercontent.com/78134028/110079213-2c99a800-7dcc-11eb-8b2e-c970893885f7.png)


* 배포중 부하테스트 결과 - 100% 성공
siege -c10 -t180S  -v --content-type "application/json" 'http://Inventory:8080/inventories POST {"memuId":2, "quantity":1}'

![image](https://user-images.githubusercontent.com/78134028/110079769-dbd67f00-7dcc-11eb-9e22-b40c34ca3f70.png)



## Self-healing (Liveness Probe)
* inventory 서비스 deployment.yml   livenessProbe 설정을 port 8089로 변경 후 배포 하여 liveness probe 가 동작함을 확인 
```
    livenessProbe:
      httpGet:
        path: '/actuator/health'
        port: 8089
      initialDelaySeconds: 5
      periodSeconds: 5
```

![image](https://user-images.githubusercontent.com/78134028/110073731-3bc82800-7dc3-11eb-9c90-1f0fd5c2c053.png)

![image](https://user-images.githubusercontent.com/78134028/110073637-0c192000-7dc3-11eb-9662-62e0ec00453e.png)





