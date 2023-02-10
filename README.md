# socket_hijack_proxy

Hijack the origin socket of Tomcat's server and open a socks5 server via this socket.

通过tomcat Filter内存马的方式，劫持tomcat原始的socket，与原始socket建立长链接，然后利用此长连接进行socks5协议的代理数据传输。在渗透测试过程中，用此方式可以隐藏代理数据的流量，目标服务器不会产生额外的http请求日志，也不需要目标出网。

### 使用效果
![image](https://user-images.githubusercontent.com/13733408/217980316-59853701-0a11-48cc-ba9d-60be391a28b8.png)
![proxy-test](https://user-images.githubusercontent.com/13733408/217980585-6882f833-ddcf-41bb-8b91-921fd1a9cb73.gif)

### 交互流程
![image](https://user-images.githubusercontent.com/13733408/217981043-34a46c3e-6a85-4e8e-8176-c6e12e3f3254.png)

### 线程模型
为提高代理效率，代理客户端可以同时处理多个代理请求，复用同一条socket长连接传输数据。在socket通道中，通过封装代理请求id，来解决通道复用问题。
![image](https://user-images.githubusercontent.com/13733408/217980929-c9fcd13e-00eb-43ae-a485-f32ba79f0453.png)
