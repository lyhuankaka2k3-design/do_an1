/*
 Combined enhanced CafeApp.java
 - Single-file Java Swing application with role-based access (ADMIN, ORDER, WAREHOUSE)
 - Added: Revenue filtering by date range and by month, CSV export for revenue, improved UI theme/colors and layout, SQL schema + seed script included as comment, placeholder for PDF export with instructions (requires external library), and a JavaFX skeleton class for future porting.
 - Usage: open in NetBeans, add MS SQL JDBC driver to project libraries.
 - Configure DB connection in DBHelper.URL / USER / PASS

 SQL Schema & Seed (run once in SQL Server Management Studio or let program create tables automatically):

 -- schema.sql
 -- CREATE DATABASE QuanLyCafe;
 -- USE QuanLyCafe;
 IF OBJECT_ID('dbo.Users','U') IS NULL
 CREATE TABLE dbo.Users(
   id INT IDENTITY(1,1) PRIMARY KEY,
   username NVARCHAR(50) UNIQUE,
   pass NVARCHAR(100),
   role NVARCHAR(20)
 );

 IF OBJECT_ID('dbo.Products','U') IS NULL
 CREATE TABLE dbo.Products(
   id INT IDENTITY(1,1) PRIMARY KEY,
   name NVARCHAR(100),
   price DECIMAL(18,2),
   qty INT,
   img NVARCHAR(300)
 );

 IF OBJECT_ID('dbo.Customers','U') IS NULL
 CREATE TABLE dbo.Customers(
   id INT IDENTITY(1,1) PRIMARY KEY,
   name NVARCHAR(100),
   phone NVARCHAR(20),
   isLoyal BIT
 );

 IF OBJECT_ID('dbo.Orders','U') IS NULL
 CREATE TABLE dbo.Orders(
   id INT IDENTITY(1,1) PRIMARY KEY,
   customerName NVARCHAR(100),
   customerPhone NVARCHAR(20),
   total DECIMAL(18,2),
   createdAt DATETIME DEFAULT GETDATE()
 );

 IF OBJECT_ID('dbo.OrderItems','U') IS NULL
 CREATE TABLE dbo.OrderItems(
   id INT IDENTITY(1,1) PRIMARY KEY,
   orderId INT,
   productId INT,
   qty INT,
   price DECIMAL(18,2)
 );

 -- Seed sample users
 INSERT INTO dbo.Users(username,pass,role) VALUES('admin','admin','ADMIN');
 INSERT INTO dbo.Users(username,pass,role) VALUES('staff','staff','ORDER');
 INSERT INTO dbo.Users(username,pass,role) VALUES('warehouse','warehouse','WAREHOUSE');

*/
import javax.swing.Timer;


import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.RowFilter;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.plaf.basic.BasicButtonUI;


/*
 Enhanced single-file Swing cafe management app.
 - Revenue export to CSV implemented (simple, no external libs).
 - PDF export: placeholder method with instructions to add iText or OpenPDF library.
 - UI theme: header, colors, nicer layout.
 - JavaFX: small skeleton class CafeAppFX for future port.
*/

public class CafeApp {
     
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}

// ---------------------- DB Helper ----------------------
class DBHelper {
    // TODO: change these values to match your environment
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=CafeApp;encrypt=false;";
    private static final String USER = "sa";
    private static final String PASS = "040203";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // Convenience: create required tables if they don't exist (basic schema)
    public static void ensureSchema() {
        String[] stmts = new String[]{
            "IF OBJECT_ID('dbo.Users','U') IS NULL CREATE TABLE dbo.Users(id INT IDENTITY(1,1) PRIMARY KEY, username NVARCHAR(50) UNIQUE, pass NVARCHAR(100), role NVARCHAR(20));",
            "IF OBJECT_ID('dbo.Products','U') IS NULL CREATE TABLE dbo.Products(id INT IDENTITY(1,1) PRIMARY KEY, name NVARCHAR(100), price DECIMAL(18,2), stock INT, img NVARCHAR(300));",
            "IF OBJECT_ID('dbo.Customers','U') IS NULL CREATE TABLE dbo.Customers(id INT IDENTITY(1,1) PRIMARY KEY, name NVARCHAR(100), phone NVARCHAR(20), isLoyal BIT);",
            "IF OBJECT_ID('dbo.Orders','U') IS NULL CREATE TABLE dbo.Orders( id INT IDENTITY(1,1) PRIMARY KEY,customer_id INT NULL REFERENCES dbo.Customers(id),total DECIMAL(18,2) NOT NULL,created_at DATETIME DEFAULT GETDATE());",
            "IF OBJECT_ID('dbo.OrderItems','U') IS NULL CREATE TABLE dbo.OrderItems(id INT IDENTITY(1,1) PRIMARY KEY, orderId INT, productId INT, stock INT, price DECIMAL(18,2));"
        };
        try (Connection c = getConnection()) {
            for (String s : stmts) try (Statement st = c.createStatement()) { st.execute(s); }
        } catch (Exception ex) {
            System.err.println("Could not ensure schema: " + ex.getMessage());
        }
    }
}

// ---------------------- Login Frame ----------------------

class LoginFrame extends JFrame {
    private JTextField tfUser = new JTextField(18);
    private JPasswordField pfPass = new JPasswordField(18);

    public LoginFrame() {
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0)); // nền JFrame trong suốt

        setTitle("Đăng nhập - Quản lý quán cafe");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 500);   // to hơn
        setLocationRelativeTo(null);
        init();
        DBHelper.ensureSchema();
        seedAdminIfNeeded();

        // Animation fade in
        setOpacity(0f);
        new Timer(15, new ActionListener() {
            float alpha = 0f;
            @Override
            public void actionPerformed(ActionEvent e) {
                alpha += 0.05f;
                setOpacity(Math.min(alpha, 1f));
                if (alpha >= 1f) ((Timer) e.getSource()).stop();
            }
        }).start();
    }

   private void init() {
    JPanel root = new JPanel(new BorderLayout());
    root.setOpaque(false); // Không vẽ nền cho root

    // Panel chứa card trắng ở giữa
    JPanel card = new RoundedPanel(40); // bo góc 40px
    card.setLayout(new GridLayout(1, 2));
    card.setBackground(Color.WHITE);
    card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

    // Bên trái: Ảnh chiếm hết 1 nửa
    ImageIcon icon = new ImageIcon(getClass().getResource("/logo.jpg"));
    Image img = icon.getImage();

    JPanel leftPanel = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(img, 0, 0, getWidth(), getHeight(), this); // ảnh fill full panel
        }
    };
    leftPanel.setOpaque(false);

    // Bên phải: Form đăng nhập
    JPanel rightPanel = new JPanel(new GridBagLayout());
    rightPanel.setOpaque(false);

    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(15, 12, 15, 12);
    c.fill = GridBagConstraints.HORIZONTAL;

    // Tiêu đề
    JLabel title = new JLabel("LOGIN", SwingConstants.CENTER);
    title.setFont(new Font("Segoe UI", Font.BOLD, 50)); // chữ LOGIN to hơn
    c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
    rightPanel.add(title, c);

    // Username
    c.gridy = 1; c.gridwidth = 1; c.gridx = 0;
    JLabel lblUser = new JLabel("Tài khoản:");
    lblUser.setFont(new Font("Segoe UI", Font.PLAIN, 18)); // chữ to hơn
    rightPanel.add(lblUser, c);

    c.gridx = 1;
    styleField(tfUser);
    rightPanel.add(tfUser, c);

    // Password
    c.gridy = 2; c.gridx = 0;
    JLabel lblPass = new JLabel("Mật khẩu:");
    lblPass.setFont(new Font("Segoe UI", Font.PLAIN, 18)); // chữ to hơn
    rightPanel.add(lblPass, c);

    c.gridx = 1;
    styleField(pfPass);
    rightPanel.add(pfPass, c);

    // Nút
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
    buttonPanel.setOpaque(false);

    JButton btnLogin = new JButton("Đăng nhập");
    stylePillButton(btnLogin, new Color(52, 199, 89), Color.WHITE);
    btnLogin.addActionListener(e -> login());

    JButton btnExit = new JButton("Thoát");
    // nền đỏ, chữ trắng
    stylePillButton(btnExit, new Color(220, 53, 69), Color.WHITE);
    btnExit.addActionListener(e -> System.exit(0));

    buttonPanel.add(btnExit);
    buttonPanel.add(btnLogin);

    c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
    rightPanel.add(buttonPanel, c);

    // Gộp hai panel vào card
    card.add(leftPanel);
    card.add(rightPanel);

    root.add(card, BorderLayout.CENTER);
    getContentPane().add(root);
}

    // Ô nhập bo tròn, to hơn
    private void styleField(JTextField field) {
    field.setFont(new Font("Segoe UI", Font.PLAIN, 16));
    field.setPreferredSize(new Dimension(250, 35)); // chiều rộng 250px, cao 35px
    field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
    ));
}


    // Button pill (bo tròn giống nhau cho cả 2 nút)
    private void stylePillButton(JButton btn, Color bg, Color fg) {
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(14, 40, 14, 40));

        btn.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Vẽ nền bo tròn
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 50, 50);

                // Nếu nền trắng thì thêm viền xanh
                if (bg.equals(Color.WHITE)) {
                    g2.setColor(new Color(52, 199, 89));
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 50, 50);
                }

                // Vẽ chữ căn giữa
                FontMetrics fm = g2.getFontMetrics(btn.getFont());
                String text = btn.getText();
                int textWidth = fm.stringWidth(text);
                int textHeight = fm.getAscent();

                int x = (c.getWidth() - textWidth) / 2;
                int y = (c.getHeight() + textHeight) / 2 - 4;

                g2.setColor(fg);
                g2.setFont(btn.getFont());
                g2.drawString(text, x, y);

                g2.dispose();
            }
        });
    }

    // Hàm scale ảnh nét hơn
    private Image getScaledImage(Image src, int w, int h) {
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return resized;
    }

    private void seedAdminIfNeeded() {
        try (Connection c = DBHelper.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM dbo.Users")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO dbo.Users(username,password,role) VALUES(?,?,?)")) {
                    ins.setString(1, "admin"); ins.setString(2, "admin"); ins.setString(3, "ADMIN"); ins.executeUpdate();
                    ins.setString(1, "staff"); ins.setString(2, "staff"); ins.setString(3, "ORDER"); ins.executeUpdate();
                    ins.setString(1, "warehouse"); ins.setString(2, "warehouse"); ins.setString(3, "WAREHOUSE"); ins.executeUpdate();
                }
            }
        } catch (Exception ex) {
            System.err.println("Seed user failed: " + ex.getMessage());
        }
    }

    private void login() {
        String user = tfUser.getText().trim();
        String pass = new String(pfPass.getPassword());
        if (user.isEmpty()) { JOptionPane.showMessageDialog(this, "Nhập tài khoản"); return; }
        try (Connection c = DBHelper.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id,role FROM dbo.Users WHERE username=? AND password=?")) {
            ps.setString(1, user);
            ps.setString(2, pass);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String role = rs.getString("role");
                int userId = rs.getInt("id");
                dispose();
                new MainFrame(user, role, userId).setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Đăng nhập thất bại");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối: " + ex.getMessage());
        }
    }

    // Panel bo góc
    class RoundedPanel extends JPanel {
        private int radius;

        public RoundedPanel(int radius) {
            super();
            this.radius = radius;
            setOpaque(false); // để trong suốt
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // Nền bo tròn
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, width - 1, height - 1, radius, radius);

            // Viền bo tròn
            g2.setColor(new Color(200, 200, 200));
            g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);

            g2.dispose();
            // KHÔNG gọi super.paintComponent(g) => không bị nền vuông
        }
    }
}






class MainFrame extends JFrame {
    private String username;
    private String role;
    private int userId;
    private CardLayout cardLayout = new CardLayout();
    private JPanel centerPanel;

    // panels
    private OrderPanel orderPanel;
    private InventoryPanel inventoryPanel;
    private ProductsPanel productsPanel;
    private CustomersPanel customersPanel;
    private AccountsPanel accountsPanel;
    private RevenuePanel revenuePanel;

    public MainFrame(String username, String role, int userId) {
        this.username = username;
        this.role = role;
        this.userId = userId;

        setTitle("Quản lý quán cafe - " + username + " (" + role + ")");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        initUI();
    }

    private void initUI() {
        // ====== Sidebar ======
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBackground(new Color(245, 245, 245));
        left.setPreferredSize(new Dimension(240, getHeight()));

        JLabel logo = new JLabel(" CafeManager", SwingConstants.CENTER);
        logo.setForeground(new Color(0, 102, 204));
        logo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        logo.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        left.add(logo);
        left.add(Box.createVerticalStrut(10));

        JButton btnTrangChu = themedButton("Trang chủ");
        JButton btnSetting  = themedButton("Setting");
        left.add(btnTrangChu);
        left.add(Box.createVerticalStrut(6));

        JPanel placeholder = new JPanel();
        placeholder.setLayout(new BoxLayout(placeholder, BoxLayout.Y_AXIS));
        placeholder.setOpaque(false);
        placeholder.add(btnSetting);

        left.add(placeholder);
        left.add(Box.createVerticalStrut(6));

        JPanel menuMain = new JPanel();
        menuMain.setLayout(new BoxLayout(menuMain, BoxLayout.Y_AXIS));
        menuMain.setOpaque(false);
        menuMain.setVisible(false);

        JButton btnHome      = themedButton(" Order");
        JButton btnRevenue   = themedButton(" Doanh thu");
        JButton btnInventory = themedButton(" Nhập xuất kho");
        JButton btnProducts  = themedButton(" Danh mục sản phẩm");
        JButton btnCustomers = themedButton(" Khách hàng thân thiết");
        JButton btnAccounts  = themedButton(" Quản lý tài khoản");

        menuMain.add(btnHome);      menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnRevenue);   menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnInventory); menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnProducts);  menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnCustomers); menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnAccounts);

        left.add(menuMain);

        JPanel settingMenu = new JPanel();
        settingMenu.setLayout(new BoxLayout(settingMenu, BoxLayout.Y_AXIS));
        settingMenu.setOpaque(false);
        settingMenu.setVisible(false);

        JButton btnRefresh = themedButton(" Refresh  ");
        JButton btnLogout  = themedButton("Đăng xuất ");
        settingMenu.add(Box.createVerticalStrut(6));
        settingMenu.add(btnRefresh);
        settingMenu.add(Box.createVerticalStrut(6));
        settingMenu.add(btnLogout);
        left.add(settingMenu);

        // ====== Center panels ======
        centerPanel = new JPanel(cardLayout);

        orderPanel     = new OrderPanel();
        inventoryPanel = new InventoryPanel();
        productsPanel  = new ProductsPanel();
        customersPanel = new CustomersPanel();
        accountsPanel  = new AccountsPanel();
        revenuePanel   = new RevenuePanel();

        centerPanel.add(new TrangChuPanel(), "HOME");
        centerPanel.add(orderPanel, "ORDER");
        centerPanel.add(revenuePanel, "REVENUE");
        centerPanel.add(inventoryPanel, "INVENTORY");
        centerPanel.add(productsPanel, "PRODUCTS");
        centerPanel.add(customersPanel, "CUSTOMERS");
        centerPanel.add(accountsPanel, "ACCOUNTS");

        getContentPane().add(left, BorderLayout.WEST);
        getContentPane().add(centerPanel, BorderLayout.CENTER);

        // ====== Sự kiện ======
        btnTrangChu.addActionListener(e -> {
            boolean show = !menuMain.isVisible();
            menuMain.setVisible(show);

            if (show) {
                if (placeholder.isAncestorOf(btnSetting)) {
                    placeholder.remove(btnSetting);
                    menuMain.add(Box.createVerticalStrut(6));
                    menuMain.add(btnSetting);
                }
                settingMenu.setVisible(false);
                cardLayout.show(centerPanel, "HOME");
            } else {
                if (menuMain.isAncestorOf(btnSetting)) {
                    menuMain.remove(btnSetting);
                    placeholder.add(btnSetting);
                }
            }
            left.revalidate();
            left.repaint();
        });

        btnSetting.addActionListener(e -> {
            boolean show = !settingMenu.isVisible();
            settingMenu.setVisible(show);
            if (show) {
                if (menuMain.isVisible()) {
                    menuMain.setVisible(false);
                    if (menuMain.isAncestorOf(btnSetting)) {
                        menuMain.remove(btnSetting);
                        placeholder.add(btnSetting);
                    }
                }
            }
            left.revalidate();
            left.repaint();
        });

        btnHome.addActionListener(e      -> cardLayout.show(centerPanel, "ORDER"));
        btnRevenue.addActionListener(e   -> cardLayout.show(centerPanel, "REVENUE"));
        btnInventory.addActionListener(e -> cardLayout.show(centerPanel, "INVENTORY"));
        btnProducts.addActionListener(e  -> cardLayout.show(centerPanel, "PRODUCTS"));
        btnCustomers.addActionListener(e -> cardLayout.show(centerPanel, "CUSTOMERS"));
        btnAccounts.addActionListener(e  -> cardLayout.show(centerPanel, "ACCOUNTS"));

        btnRefresh.addActionListener(e -> refreshAll());
        btnLogout.addActionListener(e -> {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        });

        // ====== Nút Thoát ở dưới cùng ======
        left.add(Box.createVerticalGlue()); // đẩy xuống cuối
        JButton btnExit = themedButton("Thoát");
        btnExit.setBackground(new Color(220, 53, 69)); // đỏ
        btnExit.setForeground(Color.WHITE);

        btnExit.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btnExit.setBackground(new Color(200, 35, 51));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btnExit.setBackground(new Color(220, 53, 69));
            }
        });

        btnExit.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    MainFrame.this,
                    "Bạn có chắc chắn muốn thoát không?",
                    "Xác nhận thoát",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });

        left.add(Box.createVerticalStrut(10));
        left.add(btnExit);

        applyRolePermissions(btnHome, btnRevenue, btnInventory,
                             btnProducts, btnCustomers, btnAccounts);
    }

    private void refreshAll() {
        orderPanel.loadProducts();
        inventoryPanel.load();
        productsPanel.load();
        customersPanel.load();
        accountsPanel.load();
        revenuePanel.load("");
        JOptionPane.showMessageDialog(this, "Đã refresh dữ liệu!");
    }

    private JButton themedButton(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 12;
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                super.paintComponent(g);
                g2.dispose();
            }
        };

        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        b.setForeground(Color.WHITE);

        Color normal = new Color(102, 178, 255);
        Color hover  = new Color(51, 153, 255);
        b.setBackground(normal);

        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 16));
        b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { b.setBackground(hover); }
            public void mouseExited (java.awt.event.MouseEvent evt) { b.setBackground(normal); }
        });
        return b;
    }

    private void applyRolePermissions(JButton home, JButton revenue,
                                      JButton inventory, JButton products,
                                      JButton customers, JButton accounts) {
        if ("ADMIN".equalsIgnoreCase(role)) return;

        if ("ORDER".equalsIgnoreCase(role)) {
            inventory.setEnabled(false);
            products.setEnabled(false);
            customers.setEnabled(false);
            accounts.setEnabled(false);
        } else if ("WAREHOUSE".equalsIgnoreCase(role)) {
            home.setEnabled(false);
            revenue.setEnabled(false);
            products.setEnabled(false);
            customers.setEnabled(false);
            accounts.setEnabled(false);
        }
    }
}







 class TrangChuPanel extends JPanel {
    private final JLabel welcome;
    private final JLabel logoLabel;
    private final ImageIcon logoIcon;

    public TrangChuPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        welcome = new JLabel(
            "Chào mừng bạn đến với hệ thống quản lý quán cafe!",
            SwingConstants.CENTER
        );
        welcome.setFont(new Font("Arial", Font.BOLD, 32));
        welcome.setForeground(new Color(50, 50, 50));
        add(welcome, BorderLayout.NORTH);

        logoIcon = new ImageIcon(getClass().getResource("/logo.jpg"));
        logoLabel = new JLabel();
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoLabel.setVerticalAlignment(SwingConstants.CENTER);
        add(logoLabel, BorderLayout.CENTER);

        // Giữ nguyên tỉ lệ ảnh khi panel đổi kích thước
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int panelW = getWidth();
                int panelH = getHeight() - welcome.getHeight();

                int imgW = logoIcon.getIconWidth();
                int imgH = logoIcon.getIconHeight();
                double imgRatio = (double) imgW / imgH;
                double panelRatio = (double) panelW / panelH;

                int newW, newH;
                if (panelRatio > imgRatio) {
                    newH = panelH;
                    newW = (int) (newH * imgRatio);
                } else {
                    newW = panelW;
                    newH = (int) (newW / imgRatio);
                }

                Image scaled = logoIcon.getImage()
                        .getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                logoLabel.setIcon(new ImageIcon(scaled));
            }
        });

        Timer blinkTimer = new Timer(500, e -> {
            if (welcome.getForeground().equals(getBackground())) {
                welcome.setForeground(new Color(50, 50, 50));
            } else {
                welcome.setForeground(getBackground());
            }
        });
        blinkTimer.start();
    }
}

class OrderPanel extends JPanel {
    private JTable tblProducts = new JTable();
    private JTable tblCart = new JTable();
    private DefaultTableModel productModel = new DefaultTableModel(new String[]{"ID","Tên","Giá","Tồn kho"},0);
    private DefaultTableModel cartModel    = new DefaultTableModel(new String[]{"ID","Tên","Giá","SL","Tổng"},0);
    private JTextField tfCustomer = new JTextField();
    private JTextField tfPhone    = new JTextField();
    private JLabel lblTotal       = new JLabel("0");
    private JTextField tfSearch   = new JTextField(); // ô tìm kiếm

    public OrderPanel() {
        setLayout(new BorderLayout());

        Font appleFont = new Font("Helvetica Neue", Font.PLAIN, 14);

        // ===== LEFT : Danh mục sản phẩm =====
        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(BorderFactory.createTitledBorder("Danh mục sản phẩm"));

        tblProducts.setModel(productModel);
        JScrollPane spProducts = new JScrollPane(tblProducts);
        left.add(spProducts, BorderLayout.CENTER);

       // Ô tìm kiếm
tfSearch.setFont(new Font("Segoe UI", Font.PLAIN, 16));  // chữ to hơn
tfSearch.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
        BorderFactory.createEmptyBorder(10, 15, 10, 15)  // padding
));

// Placeholder "Tìm kiếm tại đây..."
tfSearch.setForeground(Color.GRAY);
tfSearch.setText("Tìm kiếm tại đây...");

// Khi focus thì xóa placeholder
tfSearch.addFocusListener(new FocusAdapter() {
    @Override
    public void focusGained(FocusEvent e) {
        if (tfSearch.getText().equals("Tìm kiếm tại đây...")) {
            tfSearch.setText("");
            tfSearch.setForeground(Color.BLACK);
        }
    }
    @Override
    public void focusLost(FocusEvent e) {
        if (tfSearch.getText().isEmpty()) {
            tfSearch.setForeground(Color.GRAY);
            tfSearch.setText("Tìm kiếm tại đây...");
        }
    }
});

        left.add(tfSearch, BorderLayout.NORTH);
//-------------------------------------------------------------------- kết thúc tìm kiếm 

        JButton btnAdd = new JButton("➕ Thêm vào đơn");
        btnAdd.setBackground(new Color(88,190,129));
        btnAdd.setForeground(Color.WHITE);
        btnAdd.setFocusPainted(false);
        btnAdd.setFont(appleFont.deriveFont(Font.BOLD,14));
        btnAdd.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
        left.add(btnAdd, BorderLayout.SOUTH);

        // ===== RIGHT : Giỏ hàng + Thông tin =====
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Giỏ hàng"));
        tblCart.setModel(cartModel);
        right.add(new JScrollPane(tblCart), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(2,4,10,10));
        bottom.setBorder(BorderFactory.createTitledBorder("Thông tin đơn hàng"));

        JLabel lblCustomer = new JLabel("Khách:"); lblCustomer.setFont(appleFont);
        bottom.add(lblCustomer); bottom.add(tfCustomer);

        JLabel lblPhone = new JLabel("Phone:"); lblPhone.setFont(appleFont);
        bottom.add(lblPhone); bottom.add(tfPhone);

        JLabel lblTotalTitle = new JLabel("Tổng:"); lblTotalTitle.setFont(appleFont);
        bottom.add(lblTotalTitle); bottom.add(lblTotal);

        JButton btnPay = new JButton("💳 Thanh toán");
        btnPay.setBackground(new Color(70,145,220));
        btnPay.setForeground(Color.WHITE);
        btnPay.setFocusPainted(false);
        btnPay.setFont(appleFont.deriveFont(Font.BOLD,14));
        btnPay.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
        bottom.add(new JLabel(""));
        bottom.add(btnPay);

        right.add(bottom, BorderLayout.SOUTH);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(480, 500));

        // ===== STYLE TABLES =====
        styleTable(tblProducts, appleFont);
        styleTable(tblCart, appleFont);

        // ===== EVENTS =====
        loadProducts();
        btnAdd.addActionListener(e -> showAddDialog());
        btnPay.addActionListener(e -> payOrder());

        // Lọc sản phẩm khi gõ
        tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String kw = tfSearch.getText().trim();
                TableRowSorter<TableModel> sorter = new TableRowSorter<>(productModel);
                tblProducts.setRowSorter(sorter);
                if (kw.isEmpty()) sorter.setRowFilter(null);
                else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + kw));
            }
            public void insertUpdate(DocumentEvent e){ filter(); }
            public void removeUpdate(DocumentEvent e){ filter(); }
            public void changedUpdate(DocumentEvent e){ filter(); }
        });
    }

    private void styleTable(JTable t, Font f) {
        t.setFont(f);
        t.setRowHeight(26);
        t.getTableHeader().setFont(f.deriveFont(Font.BOLD,14));
        t.getTableHeader().setBackground(new Color(245,245,245));
        t.getTableHeader().setForeground(Color.BLACK);
        t.setShowGrid(true);
        t.setGridColor(new Color(220,220,220));
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(Object.class, center);
    }

    public void loadProducts() {
        productModel.setRowCount(0);
        try (Connection c = DBHelper.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id,name,price,stock FROM dbo.Products")) {
            while (rs.next()) {
                productModel.addRow(new Object[]{
                    rs.getInt(1), rs.getString(2), rs.getBigDecimal(3), rs.getInt(4)
                });
            }
        } catch (Exception ex) { System.err.println("Load product err: " + ex.getMessage()); }
    }

    // ===== Hộp thoại thêm vào đơn =====
 private void showAddDialog() {
    int r = tblProducts.getSelectedRow();
    if (r < 0) {
        JOptionPane.showMessageDialog(this, "Chọn sản phẩm");
        return;
    }

    String name = productModel.getValueAt(r, 1).toString();
    double price = Double.parseDouble(productModel.getValueAt(r, 2).toString());
    int id = (int) productModel.getValueAt(r, 0);

    JDialog dlg = new JDialog(
            SwingUtilities.getWindowAncestor(this),
            "Thêm vào Đơn",
            Dialog.ModalityType.APPLICATION_MODAL
    );

    dlg.setLayout(new GridBagLayout());
    dlg.setSize(400, 250);
    dlg.setLocationRelativeTo(this);

    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(10, 10, 10, 10);
    c.fill = GridBagConstraints.HORIZONTAL;

    JTextField tfName = new JTextField(name);
    tfName.setEditable(false);
    JTextField tfPrice = new JTextField(String.valueOf(price));
    tfPrice.setEditable(false);
    JTextField tfQty = new JTextField("1");

    c.gridx = 0; c.gridy = 0; dlg.add(new JLabel("Tên:"), c);
    c.gridx = 1; dlg.add(tfName, c);

    c.gridx = 0; c.gridy = 1; dlg.add(new JLabel("Giá:"), c);
    c.gridx = 1; dlg.add(tfPrice, c);

    c.gridx = 0; c.gridy = 2; dlg.add(new JLabel("Số lượng:"), c);
    c.gridx = 1; dlg.add(tfQty, c);

    // Nút
    JButton btnOk = new JButton("Xác nhận");
    JButton btnCancel = new JButton("Hủy");

    styleDialogButton(btnOk, new Color(52, 199, 89), Color.WHITE); // xanh
    styleDialogButton(btnCancel, new Color(220, 53, 69), Color.WHITE); // đỏ

    JPanel pBtn = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
    pBtn.add(btnCancel);
    pBtn.add(btnOk);

    c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
    dlg.add(pBtn, c);

    btnOk.addActionListener(ev -> {
        try {
            int qty = Integer.parseInt(tfQty.getText().trim());
            double total = price * qty;
            cartModel.addRow(new Object[]{id, name, price, qty, total});
            recalcTotal();
            dlg.dispose();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(dlg, "Số lượng không hợp lệ");
        }
    });
    btnCancel.addActionListener(ev -> dlg.dispose());

    dlg.setVisible(true);
}

// Style cho nút dialog
private void styleDialogButton(JButton btn, Color bg, Color fg) {
    btn.setFocusPainted(false);
    btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
    btn.setForeground(fg);
    btn.setBackground(bg);
    btn.setContentAreaFilled(false);
    btn.setOpaque(false);
    btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    btn.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));

    btn.setUI(new BasicButtonUI() {
        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(bg);
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 40, 40);

            FontMetrics fm = g2.getFontMetrics(btn.getFont());
            String text = btn.getText();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getAscent();

            int x = (c.getWidth() - textWidth) / 2;
            int y = (c.getHeight() + textHeight) / 2 - 4;

            g2.setColor(fg);
            g2.setFont(btn.getFont());
            g2.drawString(text, x, y);

            g2.dispose();
        }
    });
}

    private void recalcTotal() {
        double t = 0;
        for (int i=0;i<cartModel.getRowCount();i++)
            t += Double.parseDouble(cartModel.getValueAt(i,4).toString());
        lblTotal.setText(String.format("%.2f", t));
    }

    
    
    
    
    private void payOrder() {
        if (cartModel.getRowCount()==0) {
            JOptionPane.showMessageDialog(this,"Giỏ rỗng"); return;
        }
        String customer = tfCustomer.getText().trim();
        String phone    = tfPhone.getText().trim();
        double total    = Double.parseDouble(lblTotal.getText().replace(",", "").trim());

        int customerId = -1; boolean isLoyal = false;

        try (Connection c = DBHelper.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id,isLoyal FROM dbo.Customers WHERE phone=?")) {
                ps.setString(1, phone);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) { customerId = rs.getInt("id"); isLoyal = rs.getBoolean("isLoyal"); }
            }
            if (customerId == -1) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.Customers(name,phone,isLoyal) VALUES(?,?,0)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, customer);
                    ps.setString(2, phone);
                    ps.executeUpdate();
                    ResultSet gk = ps.getGeneratedKeys();
                    if (gk.next()) customerId = gk.getInt(1);
                }
            }
            double finalTotal = total * (isLoyal ? 0.9 : 1.0);

            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.Orders(customer_id,total,created_at) VALUES(?,?,GETDATE())",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, customerId);
                ps.setBigDecimal(2, new java.math.BigDecimal(finalTotal));
                ps.executeUpdate();
                ResultSet gk = ps.getGeneratedKeys(); gk.next();
                int orderId = gk.getInt(1);

                try (PreparedStatement psi = c.prepareStatement(
                        "INSERT INTO dbo.OrderItems(order_id,product_id,quantity,price) VALUES(?,?,?,?)")) {
                    for (int i=0;i<cartModel.getRowCount();i++) {
                        psi.setInt(1, orderId);
                        psi.setInt(2, Integer.parseInt(cartModel.getValueAt(i,0).toString()));
                        psi.setInt(3, Integer.parseInt(cartModel.getValueAt(i,3).toString()));
                        psi.setBigDecimal(4, new java.math.BigDecimal(cartModel.getValueAt(i,2).toString()));
                        psi.executeUpdate();

                        try (PreparedStatement pu = c.prepareStatement(
                                "UPDATE dbo.Products SET stock = stock - ? WHERE id = ?")) {
                            pu.setInt(1, Integer.parseInt(cartModel.getValueAt(i,3).toString()));
                            pu.setInt(2, Integer.parseInt(cartModel.getValueAt(i,0).toString()));
                            pu.executeUpdate();
                        }
                    }
                }
                c.commit();
                JOptionPane.showMessageDialog(this,"Thanh toán thành công. Tổng: " + finalTotal);
                cartModel.setRowCount(0); recalcTotal(); loadProducts();
            } catch (Exception ex) {
                c.rollback(); throw ex;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Thanh toán lỗi: " + ex.getMessage());
        }
    }
}


// ---------------------- Inventory Panel ----------------------
class InventoryPanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Tên","SL"},0);
    private JTable tbl = new JTable(model);
    public InventoryPanel() {
        setLayout(new BorderLayout());
        add(new JScrollPane(tbl), BorderLayout.CENTER);
        JPanel p = new JPanel();
        JButton btnIn = new JButton("Nhập hàng"); JButton btnOut = new JButton("Xuất hàng");
        p.add(btnIn); p.add(btnOut); add(p, BorderLayout.SOUTH);
        btnIn.addActionListener(e -> changeQty(true));
        btnOut.addActionListener(e -> changeQty(false));
        load();
    }
    public void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id,name,stock FROM dbo.Products")) {
            while (rs.next()) model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getInt(3)});
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }
    private void changeQty(boolean isIn) {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn mặt hàng"); return; }
        int id = (int) model.getValueAt(r,0);
        String s = JOptionPane.showInputDialog(this, "Số lượng:", "1"); if (s==null) return;
        int q = Integer.parseInt(s);
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE dbo.Products SET stock = stock + ? WHERE id = ?")) {
            ps.setInt(1, isIn ? q : -q); ps.setInt(2, id); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
    }
}

// ---------------------- Products Panel ----------------------
class ProductsPanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Tên","Giá","SL"},0);
    private JTable tbl = new JTable(model);
   public ProductsPanel() {
        setLayout(new BorderLayout());
        add(new JScrollPane(tbl), BorderLayout.CENTER);

        // 🎨 Toolbar Apple style
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        p.setBackground(new Color(245, 245, 247)); // Apple light gray

        JButton btnAdd = macButton("➕ Thêm");
        JButton btnEdit = macButton("✏️ Sửa");
        JButton btnDelete = macButton("🗑️ Xóa");

        p.add(btnAdd);
        p.add(btnEdit);
        p.add(btnDelete);
        add(p, BorderLayout.SOUTH);

        // 🎯 Sự kiện
        btnAdd.addActionListener(e -> addProduct());
        btnEdit.addActionListener(e -> editProduct());
        btnDelete.addActionListener(e -> deleteProduct());

        // 🎨 Apple style cho bảng
        tbl.setGridColor(new Color(220, 220, 220));
        tbl.setRowHeight(28);
        tbl.setFont(new Font("San Francisco", Font.PLAIN, 13));
        tbl.getTableHeader().setFont(new Font("San Francisco", Font.BOLD, 14));
        tbl.getTableHeader().setBackground(Color.WHITE);
        tbl.setShowGrid(true);

        load();
    }

    // 🎨 Button kiểu Apple
    private JButton macButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(new Color(0, 122, 255)); // Apple blue
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("San Francisco", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(120, 32));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hiệu ứng hover
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(10, 132, 255)); 
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(0, 122, 255));
            }
        });
        return btn;
    }
    public void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id,name,price,stock FROM dbo.Products")) {
            while (rs.next()) model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getBigDecimal(3), rs.getInt(4)});
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }
    private void addProduct() {
        String name = JOptionPane.showInputDialog(this, "Tên sản phẩm:"); if (name==null) return;
        String priceS = JOptionPane.showInputDialog(this, "Giá:"); if (priceS==null) return;
        String qtyS = JOptionPane.showInputDialog(this, "Số lượng:", "0"); if (qtyS==null) return;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO dbo.Products(name,price,stock) VALUES(?,?,?)")) {
            ps.setString(1, name); ps.setBigDecimal(2, new java.math.BigDecimal(priceS)); ps.setInt(3, Integer.parseInt(qtyS)); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
    }
    private void editProduct() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn sản phẩm"); return; }
        int id = (int) model.getValueAt(r,0);
        String name = JOptionPane.showInputDialog(this, "Tên mới:", model.getValueAt(r,1)); if (name==null) return;
        String priceS = JOptionPane.showInputDialog(this, "Giá mới:", model.getValueAt(r,2)); if (priceS==null) return;
        String qtyS = JOptionPane.showInputDialog(this, "Số lượng mới:", model.getValueAt(r,3)); if (qtyS==null) return;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE dbo.Products SET name=?,price=?,stock=? WHERE id=?")) {
            ps.setString(1, name); ps.setBigDecimal(2, new java.math.BigDecimal(priceS)); ps.setInt(3, Integer.parseInt(qtyS)); ps.setInt(4, id); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
    }
    private void deleteProduct() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn sản phẩm"); return; }
        int id = (int) model.getValueAt(r,0);
        if (JOptionPane.showConfirmDialog(this, "Xác nhận xóa?")!=0) return;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.Products WHERE id=?")) {
            ps.setInt(1,id); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
    }
}

// ---------------------- Customers Panel ----------------------
class CustomersPanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Tên","Phone","Thân thiết"},0);
    private JTable tbl = new JTable(model);
    public CustomersPanel() {
        setLayout(new BorderLayout()); add(new JScrollPane(tbl), BorderLayout.CENTER);
        JPanel p = new JPanel(); JButton add = new JButton("Thêm"); JButton edit = new JButton("Sửa"); JButton del = new JButton("Xóa");
        p.add(add); p.add(edit); p.add(del); add(p, BorderLayout.SOUTH);
        add.addActionListener(e -> addCustomer()); edit.addActionListener(e -> editCustomer()); del.addActionListener(e -> deleteCustomer()); load();
    }
     void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id,name,phone,isLoyal FROM dbo.Customers")) {
            while (rs.next()) model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)});
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }
    private void addCustomer() {
        String name = JOptionPane.showInputDialog(this, "Tên:"); if (name==null) return;
        String phone = JOptionPane.showInputDialog(this, "Phone:"); if (phone==null) return;
        int loyal = JOptionPane.showConfirmDialog(this, "Là khách thân thiết?", "", JOptionPane.YES_NO_OPTION)==0?1:0;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO dbo.Customers(name,phone,isLoyal) VALUES(?,?,?)")) {
            ps.setString(1,name); ps.setString(2,phone); ps.setBoolean(3, loyal==1); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }
    private void editCustomer() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn khách"); return; }
        int id = (int) model.getValueAt(r,0);
        String name = JOptionPane.showInputDialog(this, "Tên:", model.getValueAt(r,1)); if (name==null) return;
        String phone = JOptionPane.showInputDialog(this, "Phone:", model.getValueAt(r,2)); if (phone==null) return;
        int loyal = JOptionPane.showConfirmDialog(this, "Là khách thân thiết?", "", JOptionPane.YES_NO_OPTION)==0?1:0;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE dbo.Customers SET name=?,phone=?,isLoyal=? WHERE id=?")) {
            ps.setString(1,name); ps.setString(2,phone); ps.setBoolean(3,loyal==1); ps.setInt(4,id); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }
    private void deleteCustomer() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn khách"); return; }
        int id = (int) model.getValueAt(r,0);
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.Customers WHERE id=?")) {
            ps.setInt(1,id); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }
}

// ---------------------- Accounts Panel ----------------------
class AccountsPanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Tài khoản","Vai trò"},0);
    private JTable tbl = new JTable(model);
    public AccountsPanel() {
        setLayout(new BorderLayout()); add(new JScrollPane(tbl), BorderLayout.CENTER);
        JPanel p = new JPanel(); JButton add = new JButton("Thêm"); JButton edit = new JButton("Sửa"); JButton del = new JButton("Xóa");
        p.add(add); p.add(edit); p.add(del); add(p, BorderLayout.SOUTH);
        add.addActionListener(e -> addAccount()); edit.addActionListener(e -> editAccount()); del.addActionListener(e -> deleteAccount()); load();
    }
    public void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id,username,role FROM dbo.Users")) {
            while (rs.next()) model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3)});
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }
    private void addAccount() {
        String user = JOptionPane.showInputDialog(this, "Tên tài khoản:"); if (user==null) return;
        String pass = JOptionPane.showInputDialog(this, "Mật khẩu:"); if (pass==null) return;
        String role = JOptionPane.showInputDialog(this, "Vai trò (ADMIN,ORDER,WAREHOUSE):"); if (role==null) return;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO dbo.Users(username,password,role) VALUES(?,?,?)")) {
            ps.setString(1,user); ps.setString(2,pass); ps.setString(3,role.toUpperCase()); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }
    private void editAccount() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn tài khoản"); return; }
        int id = (int) model.getValueAt(r,0);
        String user = JOptionPane.showInputDialog(this, "Tên tài khoản:", model.getValueAt(r,1)); if (user==null) return;
        String role = JOptionPane.showInputDialog(this, "Vai trò (ADMIN,ORDER,WAREHOUSE):", model.getValueAt(r,2)); if (role==null) return;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE dbo.Users SET username=?, role=? WHERE id=?")) {
            ps.setString(1,user); ps.setString(2,role.toUpperCase()); ps.setInt(3,id); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }
    private void deleteAccount() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn tài khoản"); return; }
        int id = (int) model.getValueAt(r,0);
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.Users WHERE id=?")) {
            ps.setInt(1,id); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }
}

// ---------------------- Revenue Panel (enhanced) ----------------------
class RevenuePanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Khách","Phone","Tổng","Ngày"},0);
    private JTable tbl = new JTable(model);
public RevenuePanel() {
    setLayout(new BorderLayout()); 
    add(new JScrollPane(tbl), BorderLayout.CENTER);

    // Panel header (toolbar)
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
    p.setBackground(new Color(245, 245, 247)); // Apple gray background

    JTextField tfSearch = new JTextField(15);
    tfSearch.setPreferredSize(new Dimension(180, 32));
    tfSearch.setFont(new Font("San Francisco", Font.PLAIN, 14));

    // Nút theo phong cách Apple
    JButton btnSearch = macButton("🔍 Tìm");
    JButton btnByDate = macButton("📅 Khoảng ngày");
    JButton btnByMonth = macButton("🗓️ Theo tháng");
    JButton btnExportCSV = macButton("💾 Xuất CSV");
    JButton btnDelete = macButton("🗑️ Xóa");

    p.add(new JLabel("Tên khách:")); 
    p.add(tfSearch); 
    p.add(btnSearch);
    p.add(btnByDate); 
    p.add(btnByMonth); 
    p.add(btnExportCSV);
    p.add(btnDelete);

    add(p, BorderLayout.NORTH);

    // Sự kiện
    btnSearch.addActionListener(e -> load(tfSearch.getText().trim()));
    btnByDate.addActionListener(e -> filterByDateRange());
    btnByMonth.addActionListener(e -> filterByMonth());
    btnExportCSV.addActionListener(e -> exportCSV());

    // Xóa dòng
    btnDelete.addActionListener(e -> {
        int row = tbl.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Hãy chọn dòng cần xóa!");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            ((DefaultTableModel) tbl.getModel()).removeRow(row);
            // Nếu có database thì gọi DELETE trong DB ở đây
        }
    });

    // 🎨 Apple style cho bảng
    tbl.setGridColor(new Color(220, 220, 220));
    tbl.setRowHeight(28);
    tbl.setFont(new Font("San Francisco", Font.PLAIN, 13));
    tbl.getTableHeader().setFont(new Font("San Francisco", Font.BOLD, 14));
    tbl.getTableHeader().setBackground(Color.WHITE);
    tbl.setShowGrid(true);

    load("");
}

// 🎨 Tạo nút kiểu Apple (bo tròn, xanh nhạt)
private JButton macButton(String text) {
    JButton btn = new JButton(text);
    btn.setFocusPainted(false);
    btn.setBackground(new Color(0, 122, 255)); // Apple blue
    btn.setForeground(Color.WHITE);
    btn.setFont(new Font("San Francisco", Font.PLAIN, 14));
    btn.setPreferredSize(new Dimension(140, 32));
    btn.setBorder(BorderFactory.createEmptyBorder(5,10,5,10));
    btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

    // Hiệu ứng hover
    btn.addMouseListener(new java.awt.event.MouseAdapter() {
        public void mouseEntered(java.awt.event.MouseEvent evt) {
            btn.setBackground(new Color(10, 132, 255)); // xanh sáng hơn khi hover
        }
        public void mouseExited(java.awt.event.MouseEvent evt) {
            btn.setBackground(new Color(0, 122, 255));
        }
    });
    return btn;
}

    public void load(String q) {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT o.id, c.name AS customerName, c.phone AS customerPhone, o.total, o.created_at AS createdAt FROM dbo.Orders o LEFT JOIN dbo.Customers c ON o.customer_id = c.id WHERE c.name LIKE ? ORDER BY o.created_at DESC")) {
            ps.setString(1, "%"+q+"%"); ResultSet rs = ps.executeQuery();
            while (rs.next()) model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getTimestamp(5)});
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }

    private void filterByDateRange() {
        try {
            String from = JOptionPane.showInputDialog(this, "Từ ngày (yyyy-MM-dd):", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            String to = JOptionPane.showInputDialog(this, "Đến ngày (yyyy-MM-dd):", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            if (from==null || to==null) return;
            model.setRowCount(0);
            try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT o.id, c.name AS customerName, c.phone AS customerPhone, o.total, o.created_at AS createdAt FROM dbo.Orders o LEFT JOIN dbo.Customers c ON o.customer_id = c.id WHERE CAST(o.created_at AS DATE) BETWEEN ? AND ? ORDER BY o.created_at DESC")) {
                ps.setString(1, from); ps.setString(2, to); ResultSet rs = ps.executeQuery();
                while (rs.next()) model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getTimestamp(5)});
            }
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
    }

    private void filterByMonth() {
        try {
            String month = JOptionPane.showInputDialog(this, "Chọn tháng (YYYY-MM):", new SimpleDateFormat("yyyy-MM").format(new Date()));
            if (month==null) return;
            String start = month + "-01";
            // naive end-of-month: let SQL compute using EOMONTH
            model.setRowCount(0);
            try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT o.id, c.name AS customerName, c.phone AS customerPhone, o.total, o.created_at AS createdAt FROM dbo.Orders o LEFT JOIN dbo.Customers c ON o.customer_id = c.id WHERE CONVERT(CHAR(7), o.created_at, 120) = ? ORDER BY o.created_at DESC")) {
                ps.setString(1, month); ResultSet rs = ps.executeQuery();
                while (rs.next()) model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getTimestamp(5)});
            }
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
    }

    private void exportCSV() {
        if (tbl.getRowCount() == 0) { JOptionPane.showMessageDialog(this, "Không có dữ liệu để xuất"); return; }
        try {
            String fname = "revenue_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new java.io.File(fname));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try (PrintWriter pw = new PrintWriter(new FileWriter(fc.getSelectedFile()))) {
                // header
                for (int c=0;c<tbl.getColumnCount();c++) {
                    pw.print(tbl.getColumnName(c)); if (c<tbl.getColumnCount()-1) pw.print(",");
                }
                pw.println();
                for (int r=0;r<tbl.getRowCount();r++) {
                    for (int c=0;c<tbl.getColumnCount();c++) {
                        Object v = tbl.getValueAt(r,c);
                        String cell = v == null ? "" : v.toString().replace("\n", " ").replace(",", ";");

                        pw.print(cell); if (c<tbl.getColumnCount()-1) pw.print(",");
                    }
                    pw.println();
                }
            }
            JOptionPane.showMessageDialog(this, "Đã xuất CSV");
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi xuất CSV: " + ex.getMessage()); }
    }
}

// ---------------------- PDF Exporter (placeholder) ----------------------
class PDFExporter {
    // For PDF export add a library (iText 5/7 or OpenPDF). Example (iText 5):
    // Document document = new Document(); PdfWriter.getInstance(document, new FileOutputStream("out.pdf")); document.open(); document.add(new Paragraph("Hello")); document.close();
    // This project does not bundle iText. If you want, I can add sample code and pom/build settings.
}

// ---------------------- JavaFX Skeleton (for future porting) ----------------------
/*
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class CafeAppFX extends Application {
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Cafe Manager - JavaFX (skeleton)");
        primaryStage.setScene(new Scene(new Label("This is a skeleton for future port to JavaFX"), 600, 400));
        primaryStage.show();
    }
    public static void main(String[] args) { launch(args); }
}
*/