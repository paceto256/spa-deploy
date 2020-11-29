docker run -p 8080:8080 -p 50000:50000 -v jenkins_home:/var/jenkins_home spa-jenkins
docker exec -u 0 -it CONTAINER_ID bash
sudo docker commit 351e26f59f1a spa-jenkins

#### RUN
```
aws ec2 run-instances \
  --image-id ami-066b4fb6b0f1b4a42 \
  --count 1 \
  --instance-type t3.small \
  --key-name jenkinsaws \
  --security-group-ids sg-0caa4646944cb6c4f \
  --subnet-id subnet-028ff45ec9205320f \
  --associate-public-ip-address \
  --query Instances[0].InstanceId
```
  
#### Create new AMI with last changes (instanceId of the provisioned ec2)
```
aws ec2 create-image --instance-id i-0ab475518cdcf45c1 --name "jenkins-aws3" --description "enkins-aws"
```