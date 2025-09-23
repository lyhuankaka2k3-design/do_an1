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

import javax.swing.border.LineBorder;

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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import static javax.swing.SwingConstants.CENTER;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.JTableHeader;


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
            // Bảng Users (đổi pass -> password để khớp với LoginFrame)
            "IF OBJECT_ID('dbo.Users','U') IS NULL " +
            "CREATE TABLE dbo.Users(" +
            "id INT IDENTITY(1,1) PRIMARY KEY, " +
            "username NVARCHAR(50) UNIQUE, " +
            "password NVARCHAR(100), " +
            "role NVARCHAR(20));",

            // Bảng Products
            "IF OBJECT_ID('dbo.Products','U') IS NULL " +
            "CREATE TABLE dbo.Products(" +
            "id INT IDENTITY(1,1) PRIMARY KEY, " +
            "name NVARCHAR(100), " +
            "price DECIMAL(18,2), " +
            "stock INT, " +
            "img NVARCHAR(300));",

            // Bảng Customers
            "IF OBJECT_ID('dbo.Customers','U') IS NULL " +
            "CREATE TABLE dbo.Customers(" +
            "id INT IDENTITY(1,1) PRIMARY KEY, " +
            "name NVARCHAR(100), " +
            "phone NVARCHAR(20), " +
            "isLoyal BIT);",

            // Bảng Orders (thêm created_by)
            "IF OBJECT_ID('dbo.Orders','U') IS NULL " +
            "CREATE TABLE dbo.Orders(" +
            "id INT IDENTITY(1,1) PRIMARY KEY, " +
            "customer_id INT NULL REFERENCES dbo.Customers(id), " +
            "total DECIMAL(18,2) NOT NULL, " +
            "created_at DATETIME DEFAULT GETDATE(), " +
            "created_by INT NULL REFERENCES dbo.Users(id));",

            // Bảng OrderItems
            "IF OBJECT_ID('dbo.OrderItems','U') IS NULL " +
            "CREATE TABLE dbo.OrderItems(" +
            "id INT IDENTITY(1,1) PRIMARY KEY, " +
            "orderId INT, " +
            "productId INT, " +
            "stock INT, " +
            "price DECIMAL(18,2));"
        };

        try (Connection c = getConnection()) {
            for (String s : stmts) {
                try (Statement st = c.createStatement()) {
                    st.execute(s);
                }
            }
        } catch (Exception ex) {
            System.err.println("Could not ensure schema: " + ex.getMessage());
        }
    }

    // ======================
    // Gộp CurrentUser vào đây
    // ======================
    public static class CurrentUser {
        public static String username = "system";
        public static int userId = -1;
        public static String role = "GUEST";
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

            // dùng DBHelper.CurrentUser (vì CurrentUser là inner class của DBHelper)
            DBHelper.CurrentUser.username = user;
            DBHelper.CurrentUser.userId = userId;
            DBHelper.CurrentUser.role = role;

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
        // ====== Sidebar với background ======
        ImageIcon sideBg = new ImageIcon(getClass().getResource("/13.jpg"));
        JPanel left = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(sideBg.getImage(), 0, 0, getWidth(), getHeight(), this);
            }
        };
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(240, getHeight()));
        left.setOpaque(false);

        JLabel logo = new JLabel(" CafeManager", SwingConstants.CENTER);
        logo.setForeground(new Color(0, 102, 204));
        logo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        logo.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        left.add(logo);
        left.add(Box.createVerticalStrut(10));

        // ====== Nút chính ======
        JButton btnTrangChu = themedButton("Trang chủ",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(102,178,255), new Color(51,153,255));

        left.add(btnTrangChu);
        left.add(Box.createVerticalStrut(6));

        // ====== Menu chính ======
        JPanel menuMain = new JPanel();
        menuMain.setLayout(new BoxLayout(menuMain, BoxLayout.Y_AXIS));
        menuMain.setOpaque(false);

        JButton btnHome = themedButton(" Order",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(102,178,255), new Color(51,153,255));

        JButton btnRevenue = themedButton(" Doanh thu",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(102,178,255), new Color(51,153,255));

        JButton btnInventory = themedButton(" Nhập xuất kho",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(102,178,255), new Color(51,153,255));

        JButton btnProducts = themedButton(" Danh mục sản phẩm",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(102,178,255), new Color(51,153,255));

        JButton btnCustomers = themedButton(" Khách hàng thân thiết",
                new ImageIcon(getClass().getResource("/icon_1.png")),
               new Color(102,178,255), new Color(51,153,255));

        JButton btnAccounts = themedButton(" Quản lý tài khoản",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(102,178,255), new Color(51,153,255));

        JButton btnSetting = themedButton(" Setting",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(255,102,178), new Color(235,82,158));

        menuMain.add(btnHome);      menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnRevenue);   menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnInventory); menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnProducts);  menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnCustomers); menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnAccounts);  menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnSetting);

        left.add(menuMain);

        // ====== Setting menu ======
        JPanel settingMenu = new JPanel();
        settingMenu.setLayout(new BoxLayout(settingMenu, BoxLayout.Y_AXIS));
        settingMenu.setOpaque(false);
        settingMenu.setVisible(false);

        JButton btnRefresh = themedButton(" Refresh",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(0,151,196), new Color(0,131,176));

        JButton btnLogout = themedButton(" Đăng xuất",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(248,92,80), new Color(228,72,60));

        settingMenu.add(Box.createVerticalStrut(6));
        settingMenu.add(btnRefresh);
        settingMenu.add(Box.createVerticalStrut(6));
        settingMenu.add(btnLogout);
        left.add(settingMenu);

        // ====== Center panels ======
        centerPanel = new JPanel(cardLayout);
        centerPanel.setOpaque(true);
        centerPanel.setBackground(Color.WHITE);

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
        btnTrangChu.addActionListener(e -> cardLayout.show(centerPanel, "HOME"));

        btnSetting.addActionListener(e -> {
            boolean show = !settingMenu.isVisible();
            settingMenu.setVisible(show);
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

        // ====== Nút Thoát ======
        left.add(Box.createVerticalGlue());
        JButton btnExit = themedButton("Thoát",
                new ImageIcon(getClass().getResource("/icon_1.png")),
                new Color(220, 53, 69), new Color(200, 35, 51));

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

    // ====== Nút có hỗ trợ icon + màu ======
    private JButton themedButton(String text, Icon icon, Color bgColor, Color hoverColor) {
        JButton b = new JButton(text, icon) {
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

        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setHorizontalTextPosition(SwingConstants.RIGHT);
        b.setIconTextGap(15);

        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        b.setForeground(Color.WHITE);
        b.setBackground(bgColor);

        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 16));
        b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        // Hover + hiệu ứng phóng to nhẹ
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { 
                b.setBackground(hoverColor);
                b.setFont(b.getFont().deriveFont(18f)); // phóng to nhẹ
            }
            public void mouseExited (java.awt.event.MouseEvent evt) { 
                b.setBackground(bgColor);
                b.setFont(b.getFont().deriveFont(16f)); // trở về bình thường
            }
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
            "",
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
    private JTextField tfSearch   = new JTextField();

    public OrderPanel() {
        setLayout(new BorderLayout());
        Font appleFont = new Font("Helvetica Neue", Font.PLAIN, 14);

        // LEFT : Products
        JPanel left = new JPanel(new BorderLayout(10,10));
        left.setBorder(BorderFactory.createTitledBorder("Danh mục sản phẩm"));

        tblProducts.setModel(productModel);
        JScrollPane spProducts = new JScrollPane(tblProducts);
        left.add(spProducts, BorderLayout.CENTER);

        // Search bar
        JPanel searchPanel = new JPanel(new BorderLayout(5,5));
        tfSearch.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        tfSearch.setBorder(new LineBorder(new Color(200,200,200),1,true));
        tfSearch.setForeground(Color.GRAY);
        tfSearch.setText("Tìm kiếm tại đây...");
        tfSearch.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (tfSearch.getText().equals("Tìm kiếm tại đây...")) {
                    tfSearch.setText(""); tfSearch.setForeground(Color.BLACK);
                }
            }
            public void focusLost(FocusEvent e) {
                if (tfSearch.getText().isEmpty()) {
                    tfSearch.setForeground(Color.GRAY);
                    tfSearch.setText("Tìm kiếm tại đây...");
                }
            }
        });
        JButton btnSearch = macButton("🔍", new Color(88,190,129));
        searchPanel.add(tfSearch, BorderLayout.CENTER);
        searchPanel.add(btnSearch, BorderLayout.EAST);
        left.add(searchPanel, BorderLayout.NORTH);

        JButton btnAdd = macButton("➕ Thêm vào đơn", new Color(88,190,129));
        left.add(btnAdd, BorderLayout.SOUTH);

        // RIGHT : Cart
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Giỏ hàng"));
        tblCart.setModel(cartModel);
        right.add(new JScrollPane(tblCart), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(2,4,10,10));
        bottom.setBorder(BorderFactory.createTitledBorder("Thông tin đơn hàng"));

        bottom.add(new JLabel("Khách:")); bottom.add(tfCustomer);
        bottom.add(new JLabel("Phone:"));  bottom.add(tfPhone);
        bottom.add(new JLabel("Tổng:"));   bottom.add(lblTotal);

        JButton btnPay = macButton("💳 Thanh toán", new Color(70,145,220));
        bottom.add(new JLabel("")); bottom.add(btnPay);

        right.add(bottom, BorderLayout.SOUTH);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(480, 500));

        // STYLE
        styleTable(tblProducts, appleFont);
        styleTable(tblCart, appleFont);

        // EVENTS
        loadProducts();
        btnAdd.addActionListener(e -> showAddDialog());
        btnPay.addActionListener(e -> showConfirmDialog());
        btnSearch.addActionListener(e -> filterProducts(tfSearch.getText()));

        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e){ filterProducts(tfSearch.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e){ filterProducts(tfSearch.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e){ filterProducts(tfSearch.getText()); }
        });
    }

    // Button style
    private JButton macButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Helvetica Neue", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setBorder(new EmptyBorder(8,16,8,16));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void styleTable(JTable t, Font f) {
        t.setFont(f);
        t.setRowHeight(28);
        t.getTableHeader().setFont(f.deriveFont(Font.BOLD,14));
        t.getTableHeader().setBackground(new Color(50, 120, 220));
        t.getTableHeader().setForeground(Color.WHITE);
        t.setShowGrid(true);
        t.setGridColor(new Color(220,220,220));
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(Object.class, center);
    }

    // Add product
    private void showAddDialog() {
        int row = tblProducts.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this,"Chọn sản phẩm"); return; }
        int modelRow = tblProducts.convertRowIndexToModel(row);
        int id = (int) productModel.getValueAt(modelRow, 0);
        String name = productModel.getValueAt(modelRow, 1).toString();
        double price = Double.parseDouble(productModel.getValueAt(modelRow, 2).toString());
        int stock = (int) productModel.getValueAt(modelRow, 3);

        String qtyStr = JOptionPane.showInputDialog(this, "Nhập số lượng:", "1");
        if (qtyStr == null) return;
        int qty;
        try { qty = Integer.parseInt(qtyStr); }
        catch (Exception ex){ JOptionPane.showMessageDialog(this,"Số lượng không hợp lệ"); return; }
        if (qty<=0 || qty>stock){ JOptionPane.showMessageDialog(this,"SL không hợp lệ"); return; }

        cartModel.addRow(new Object[]{id,name,price,qty,price*qty});
        recalcTotal();
    }

    // Confirm dialog with payment method
  // ===== HỘP THOẠI XÁC NHẬN THANH TOÁN =====
private void showConfirmDialog() {
    if (cartModel.getRowCount() == 0) {
        JOptionPane.showMessageDialog(this, "Giỏ rỗng");
        return;
    }

    JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Xác nhận thanh toán", true);
    dialog.setLayout(new BorderLayout(10, 10));
    dialog.setSize(700, 450);
    dialog.setLocationRelativeTo(this);

    // ==== Thông tin khách hàng ====
    JPanel customerPanel = new JPanel(new GridLayout(2, 2, 8, 8));
    customerPanel.setBorder(BorderFactory.createTitledBorder("Khách hàng"));
    customerPanel.add(new JLabel("Tên KH:"));
    JTextField tfName = new JTextField(tfCustomer.getText());
    customerPanel.add(tfName);
    customerPanel.add(new JLabel("SĐT:"));
    JTextField tfPhone2 = new JTextField(tfPhone.getText());
    customerPanel.add(tfPhone2);

    // ==== Bảng sản phẩm ====
    JTable tblConfirm = new JTable(new DefaultTableModel(
            new String[]{"Tên", "SL", "Giá", "Tổng"}, 0
    ));
    DefaultTableModel confirmModel = (DefaultTableModel) tblConfirm.getModel();

    for (int i = 0; i < cartModel.getRowCount(); i++) {
        confirmModel.addRow(new Object[]{
                cartModel.getValueAt(i, 1),
                cartModel.getValueAt(i, 3),
                cartModel.getValueAt(i, 2),
                cartModel.getValueAt(i, 4)
        });
    }
    JScrollPane spTable = new JScrollPane(tblConfirm);
    spTable.setBorder(BorderFactory.createTitledBorder("Chi tiết đơn hàng"));

    // ==== Tổng tiền ====
    JLabel lblTotal = new JLabel();
    lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 14));
    updateConfirmTotal(confirmModel, lblTotal);

    JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    totalPanel.add(new JLabel("Tổng cộng: "));
    totalPanel.add(lblTotal);

    // ==== Hình thức thanh toán ====
    JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    paymentPanel.setBorder(BorderFactory.createTitledBorder("Thanh toán"));
    String[] options = {"Tiền mặt", "Chuyển khoản", "Thẻ", "Ví điện tử"};
    JComboBox<String> cbPayment = new JComboBox<>(options);
    paymentPanel.add(new JLabel("Hình thức:"));
    paymentPanel.add(cbPayment);

    // ==== Nút chức năng ====
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton btnEdit = new JButton("✏️ Sửa SL");
    JButton btnDelete = new JButton("🗑 Xóa SP");
    JButton btnCancel = new JButton("❌ Hủy");
    JButton btnConfirm = new JButton("✅ Xác nhận");

    styleButton(btnEdit, new Color(52, 152, 219));
    styleButton(btnDelete, new Color(231, 76, 60));
    styleButton(btnCancel, new Color(149, 165, 166));
    styleButton(btnConfirm, new Color(46, 204, 113));

    buttonPanel.add(btnEdit);
    buttonPanel.add(btnDelete);
    buttonPanel.add(btnCancel);
    buttonPanel.add(btnConfirm);

    // ==== Sự kiện nút ====
    btnEdit.addActionListener(e -> {
        int row = tblConfirm.getSelectedRow();
        if (row < 0) return;

        String newQtyStr = JOptionPane.showInputDialog(dialog, "Nhập số lượng mới:", confirmModel.getValueAt(row, 1));
        if (newQtyStr != null) {
            try {
                int newQty = Integer.parseInt(newQtyStr);
                if (newQty <= 0) {
                    JOptionPane.showMessageDialog(dialog, "Số lượng phải > 0");
                    return;
                }

                double price = Double.parseDouble(confirmModel.getValueAt(row, 2).toString());
                // update confirm
                confirmModel.setValueAt(newQty, row, 1);
                confirmModel.setValueAt(price * newQty, row, 3);

                // đồng bộ cartModel
                cartModel.setValueAt(newQty, row, 3);
                cartModel.setValueAt(price * newQty, row, 4);

                recalcTotal();
                updateConfirmTotal(confirmModel, lblTotal);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Số lượng không hợp lệ");
            }
        }
    });

    btnDelete.addActionListener(e -> {
        int row = tblConfirm.getSelectedRow();
        if (row >= 0) {
            confirmModel.removeRow(row);
            cartModel.removeRow(row);
            recalcTotal();
            updateConfirmTotal(confirmModel, lblTotal);
        }
    });

    btnCancel.addActionListener(e -> dialog.dispose());

    btnConfirm.addActionListener(e -> {
        String paymentMethod = cbPayment.getSelectedItem().toString();

        // đồng bộ KH
        tfCustomer.setText(tfName.getText());
        tfPhone.setText(tfPhone2.getText());

        JOptionPane.showMessageDialog(dialog, "Thanh toán thành công bằng " + paymentMethod);
        payOrder(paymentMethod);
        dialog.dispose();
    });

    // ==== Add vào dialog ====
    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(spTable, BorderLayout.CENTER);
    centerPanel.add(totalPanel, BorderLayout.SOUTH);

    dialog.add(customerPanel, BorderLayout.NORTH);
    dialog.add(centerPanel, BorderLayout.CENTER);
    dialog.add(paymentPanel, BorderLayout.WEST);
    dialog.add(buttonPanel, BorderLayout.SOUTH);

    dialog.setVisible(true);
}

// ==== Style button ====
private void styleButton(JButton btn, Color bg) {
    btn.setFocusPainted(false);
    btn.setBackground(bg);
    btn.setForeground(Color.WHITE);
    btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
    btn.setBorder(BorderFactory.createEmptyBorder(6, 15, 6, 15));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
}

// ==== Hàm tính tổng cho bảng confirm ====
private void updateConfirmTotal(DefaultTableModel model, JLabel lblTotal) {
    double total = 0;
    for (int i = 0; i < model.getRowCount(); i++) {
        total += Double.parseDouble(model.getValueAt(i, 3).toString());
    }
    lblTotal.setText(String.format("%,.0f VNĐ", total));
}


    private void recalcTotal() {
        double t = 0;
        for (int i=0;i<cartModel.getRowCount();i++)
            t += Double.parseDouble(cartModel.getValueAt(i,4).toString());
        lblTotal.setText(String.format("%.2f", t));
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

    private void filterProducts(String kw) {
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(productModel);
        tblProducts.setRowSorter(sorter);
        if (kw==null || kw.trim().isEmpty() || kw.equals("Tìm kiếm tại đây...")) sorter.setRowFilter(null);
        else sorter.setRowFilter(RowFilter.regexFilter("(?i)"+kw.trim()));
    }

    // Save order to DB
    private void payOrder(String paymentMethod) {
        if (cartModel.getRowCount()==0) return;
        String customer = tfCustomer.getText().trim();
        String phone    = tfPhone.getText().trim();
        double total    = Double.parseDouble(lblTotal.getText().replace(",", "").trim());

        try (Connection c = DBHelper.getConnection()) {
            int customerId = -1;
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM dbo.Customers WHERE phone=?")) {
                ps.setString(1,phone);
                ResultSet rs=ps.executeQuery();
                if(rs.next()) customerId=rs.getInt(1);
            }
            if(customerId==-1){
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.Customers(name,phone,isLoyal) VALUES(?,?,0)",Statement.RETURN_GENERATED_KEYS)){
                    ps.setString(1,customer); ps.setString(2,phone); ps.executeUpdate();
                    ResultSet gk=ps.getGeneratedKeys(); if(gk.next()) customerId=gk.getInt(1);
                }
            }

            c.setAutoCommit(false);
            int orderId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.Orders(customer_id,total,created_at,created_by,payment_method) VALUES(?,?,GETDATE(),?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1,customerId);
                ps.setBigDecimal(2,new BigDecimal(total));
                ps.setInt(3,DBHelper.CurrentUser.userId);
                ps.setString(4,paymentMethod);
                ps.executeUpdate();
                ResultSet gk=ps.getGeneratedKeys(); gk.next(); orderId=gk.getInt(1);
            }

            try (PreparedStatement psi = c.prepareStatement(
                    "INSERT INTO dbo.OrderItems(order_id,product_id,quantity,price) VALUES(?,?,?,?)")) {
                for(int i=0;i<cartModel.getRowCount();i++){
                    psi.setInt(1,orderId);
                    psi.setInt(2,Integer.parseInt(cartModel.getValueAt(i,0).toString()));
                    psi.setInt(3,Integer.parseInt(cartModel.getValueAt(i,3).toString()));
                    psi.setBigDecimal(4,new BigDecimal(cartModel.getValueAt(i,2).toString()));
                    psi.executeUpdate();
                    try (PreparedStatement pu=c.prepareStatement("UPDATE dbo.Products SET stock=stock-? WHERE id=?")){
                        pu.setInt(1,Integer.parseInt(cartModel.getValueAt(i,3).toString()));
                        pu.setInt(2,Integer.parseInt(cartModel.getValueAt(i,0).toString()));
                        pu.executeUpdate();
                    }
                }
            }
            c.commit();
            JOptionPane.showMessageDialog(this,"Thanh toán thành công bằng " + paymentMethod + ". Tổng: "+total);
            cartModel.setRowCount(0); recalcTotal(); loadProducts();
        }catch(Exception ex){ JOptionPane.showMessageDialog(this,"Thanh toán lỗi: "+ex.getMessage()); }
    }
}

// ======================= REVENUE PANEL =======================
class RevenuePanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(
        new String[]{"ID","Khách","Phone","Tổng","Ngày","Người lập","Hình thức"},0);
    private JTable tbl = new JTable(model);

    public RevenuePanel() {
        setLayout(new BorderLayout()); 
        JScrollPane sp = new JScrollPane(tbl);
        add(sp, BorderLayout.CENTER);

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        p.setBackground(new Color(245, 245, 247));

        JTextField tfSearch = new JTextField(15);
        JButton btnSearch = macButton("🔍 Tìm");
        JButton btnByDate = macButton("📅 Khoảng ngày");
        JButton btnByMonth = macButton("🗓️ Theo tháng");
        JButton btnExportCSV = macButton("💾 Xuất CSV");
        JButton btnTotal = macButton("💰 Xem tổng");

        p.add(new JLabel("Tên khách:")); 
        p.add(tfSearch); 
        p.add(btnSearch);
        p.add(btnByDate); 
        p.add(btnByMonth); 
        p.add(btnExportCSV);
        p.add(btnTotal);

        add(p, BorderLayout.NORTH);

        // Style bảng
        styleTable(tbl);

        btnSearch.addActionListener(e -> load(tfSearch.getText().trim()));
        btnByDate.addActionListener(e -> filterByDateRange());
        btnByMonth.addActionListener(e -> filterByMonth());
        btnExportCSV.addActionListener(e -> exportCSV());
        btnTotal.addActionListener(e -> showTotalRevenue());

        load("");
    }

    private JButton macButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(new Color(0, 122, 255));
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("San Francisco", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(140, 32));
        return btn;
    }

    private void styleTable(JTable t) {
        t.setRowHeight(28);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        t.getTableHeader().setBackground(new Color(0,122,204));
        t.getTableHeader().setForeground(Color.WHITE);
        t.setShowGrid(false);

        // Zebra stripe
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table,value,isSelected,hasFocus,row,column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(245,245,245));
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });
    }

    public void load(String q) {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement(
            "SELECT o.id, c.name AS customerName, c.phone AS customerPhone, " +
            "o.total, o.created_at, u.username AS createdBy, o.payment_method " +
            "FROM dbo.Orders o " +
            "LEFT JOIN dbo.Customers c ON o.customer_id = c.id " +
            "LEFT JOIN dbo.Users u ON o.created_by = u.id " +
            "WHERE c.name LIKE ? ORDER BY o.created_at DESC")) {
            ps.setString(1, "%"+q+"%"); 
            ResultSet rs = ps.executeQuery();
            while (rs.next()) 
                model.addRow(new Object[]{
                    rs.getInt("id"), rs.getString("customerName"), rs.getString("customerPhone"),
                    rs.getBigDecimal("total"), rs.getTimestamp("created_at"), rs.getString("createdBy"), rs.getString("payment_method")});
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }
    // =================== FILTER BY DATE RANGE ===================
    private void filterByDateRange() {
        try {
            String from = JOptionPane.showInputDialog(this, "Từ ngày (yyyy-MM-dd):", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            String to = JOptionPane.showInputDialog(this, "Đến ngày (yyyy-MM-dd):", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            if (from==null || to==null) return;
            model.setRowCount(0);
            try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT o.id, c.name AS customerName, c.phone AS customerPhone, " +
                "o.total, o.created_at, u.username AS createdBy " +
                "FROM dbo.Orders o " +
                "LEFT JOIN dbo.Customers c ON o.customer_id = c.id " +
                "LEFT JOIN dbo.Users u ON o.created_by = u.id " +
                "WHERE CAST(o.created_at AS DATE) BETWEEN ? AND ? ORDER BY o.created_at DESC")) {
                ps.setString(1, from); ps.setString(2, to); 
                ResultSet rs = ps.executeQuery();
                while (rs.next()) 
                    model.addRow(new Object[]{
                        rs.getInt("id"), rs.getString("customerName"), rs.getString("customerPhone"),
                        rs.getBigDecimal("total"), rs.getTimestamp("created_at"), rs.getString("createdBy")});
            }
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
    }

    // =================== FILTER BY MONTH ===================
    private void filterByMonth() {
        try {
            String month = JOptionPane.showInputDialog(this, "Chọn tháng (YYYY-MM):", new SimpleDateFormat("yyyy-MM").format(new Date()));
            if (month==null) return;
            model.setRowCount(0);
            try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT o.id, c.name AS customerName, c.phone AS customerPhone, " +
                "o.total, o.created_at, u.username AS createdBy " +
                "FROM dbo.Orders o " +
                "LEFT JOIN dbo.Customers c ON o.customer_id = c.id " +
                "LEFT JOIN dbo.Users u ON o.created_by = u.id " +
                "WHERE CONVERT(CHAR(7), o.created_at, 120) = ? ORDER BY o.created_at DESC")) {
                ps.setString(1, month); 
                ResultSet rs = ps.executeQuery();
                while (rs.next()) 
                    model.addRow(new Object[]{
                        rs.getInt("id"), rs.getString("customerName"), rs.getString("customerPhone"),
                        rs.getBigDecimal("total"), rs.getTimestamp("created_at"), rs.getString("createdBy")});
            }
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
    }

    // =================== XEM TỔNG DOANH THU ===================
    private void showTotalRevenue() {
        try {
            String from = JOptionPane.showInputDialog(this, "Từ ngày (yyyy-MM-dd):", 
                           new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            String to = JOptionPane.showInputDialog(this, "Đến ngày (yyyy-MM-dd):", 
                         new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            if (from == null || to == null) return;

            try (Connection c = DBHelper.getConnection(); 
                 PreparedStatement ps = c.prepareStatement(
                "SELECT SUM(o.total) FROM dbo.Orders o " +
                "WHERE CAST(o.created_at AS DATE) BETWEEN ? AND ?")) {
                ps.setString(1, from); 
                ps.setString(2, to);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    double sum = rs.getDouble(1);
                    JOptionPane.showMessageDialog(this, 
                        "Tổng doanh thu từ " + from + " đến " + to + " = " + sum + " VNĐ",
                        "Kết quả", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }

    // =================== EXPORT CSV (giữ nguyên) ===================
    private void exportCSV() {
        if (tbl.getRowCount() == 0) { JOptionPane.showMessageDialog(this, "Không có dữ liệu để xuất"); return; }
        try {
            String fname = "revenue_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new java.io.File(fname));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try (PrintWriter pw = new PrintWriter(new FileWriter(fc.getSelectedFile()))) {
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




//____________________kho __________________________________________________________

// ---------------------- Inventory Panel ----------------------
class InventoryPanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Tên","SL"},0);
    private JTable tbl = new JTable(model);
    private JTextField txtSearch = new JTextField(20);

    public InventoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(230, 245, 255)); // nền xanh nhạt

        // ==== Thanh tìm kiếm ====
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBackground(new Color(230, 245, 255));
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JButton btnSearch = modernButton("Tìm", new Color(0, 123, 255));

        searchPanel.add(new JLabel("Tìm sản phẩm:"));
        searchPanel.add(txtSearch);
        searchPanel.add(btnSearch);

        add(searchPanel, BorderLayout.NORTH);

        // ==== Tùy chỉnh bảng ====
        tbl.setRowHeight(28);
        tbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tbl.setFillsViewportHeight(true);

        JTableHeader header = tbl.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setBackground(new Color(0, 123, 255));
        header.setForeground(Color.WHITE);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i=0; i<tbl.getColumnCount(); i++) {
            tbl.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    if (row % 2 == 0) c.setBackground(new Color(240, 248, 255));
                    else c.setBackground(Color.WHITE);
                } else {
                    c.setBackground(new Color(173, 216, 230));
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        add(new JScrollPane(tbl), BorderLayout.CENTER);

        // ==== Panel nút nhập/xuất ====
        JPanel p = new JPanel();
        p.setBackground(new Color(230, 245, 255));
        JButton btnIn = modernButton("Nhập hàng", new Color(0, 123, 255));
        JButton btnOut = modernButton("Xuất hàng", new Color(40, 167, 69));
        p.add(btnIn); p.add(btnOut);
        add(p, BorderLayout.SOUTH);

        // ==== Sự kiện ====
        btnIn.addActionListener(e -> changeQty(true));
        btnOut.addActionListener(e -> changeQty(false));

        btnSearch.addActionListener(e -> search());
        txtSearch.addActionListener(e -> search()); // enter để tìm

        load();
    }

    // ==== Load tất cả sản phẩm ====
    public void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id,name,stock FROM dbo.Products")) {
            while (rs.next())
                model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getInt(3)});
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }

    // ==== Tìm kiếm sản phẩm ====
    private void search() {
        String keyword = txtSearch.getText().trim();
        model.setRowCount(0);
        String sql = "SELECT id,name,stock FROM dbo.Products WHERE name LIKE ?";
        try (Connection c = DBHelper.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getInt(3)});
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi tìm kiếm: " + ex.getMessage());
        }
    }

    private void changeQty(boolean isIn) {
        int r = tbl.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this, "Chọn mặt hàng"); return; }
        int id = (int) model.getValueAt(r,0);
        String s = JOptionPane.showInputDialog(this, "Số lượng:", "1");
        if (s==null) return;
        int q = Integer.parseInt(s);
        try (Connection c = DBHelper.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE dbo.Products SET stock = stock + ? WHERE id = ?")) {
            ps.setInt(1, isIn ? q : -q);
            ps.setInt(2, id);
            ps.executeUpdate();
            load();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }

    // ====== Button hiện đại có hiệu ứng hover ======
    private JButton modernButton(String text, Color baseColor) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setForeground(Color.WHITE);
        b.setBackground(baseColor);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setPreferredSize(new Dimension(120, 35));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));

        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                b.setBackground(baseColor.darker());
                b.setPreferredSize(new Dimension(130, 38));
                b.revalidate();
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                b.setBackground(baseColor);
                b.setPreferredSize(new Dimension(120, 35));
                b.revalidate();
            }
        });
        return b;
    }
}

///__________________________________________danh mục sản phẩm ________________________________________

// ---------------------- Products Panel ----------------------

class ProductsPanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Tên","Giá","SL"},0);
    private JTable tbl = new JTable(model);
    private JTextField tfSearch = new JTextField(20);

    public ProductsPanel() {
        setLayout(new BorderLayout(10,10));

        // 🔍 Thanh tìm kiếm
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,10,10));

        JLabel lblSearch = new JLabel("🔍 Tìm kiếm:");
        lblSearch.setFont(new Font("San Francisco", Font.BOLD, 16)); // chữ to hơn

        tfSearch.setFont(new Font("San Francisco", Font.PLAIN, 16)); // chữ trong ô nhập to
        tfSearch.setPreferredSize(new Dimension(250, 35));

        JButton btnSearch = new JButton("Tìm");
        btnSearch.setFont(new Font("San Francisco", Font.BOLD, 15));
        btnSearch.setBackground(new Color(0,122,255));
        btnSearch.setForeground(Color.WHITE);
        btnSearch.setFocusPainted(false);
        btnSearch.setPreferredSize(new Dimension(90, 35));

        // hover xanh đậm hơn
        btnSearch.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btnSearch.setBackground(new Color(10,132,255));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btnSearch.setBackground(new Color(0,122,255));
            }
        });

        searchPanel.add(lblSearch);
        searchPanel.add(tfSearch);
        searchPanel.add(btnSearch);

        add(searchPanel, BorderLayout.NORTH);

        btnSearch.addActionListener(e -> load(tfSearch.getText().trim()));

        add(new JScrollPane(tbl), BorderLayout.CENTER);

        // 🎨 Toolbar
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        p.setBackground(new Color(245, 245, 247));

        JButton btnAdd = macButton("➕ Thêm", new Color(0,122,255));
        JButton btnEdit = macButton("✏️ Sửa", new Color(0,122,255));
        JButton btnDelete = macButton("🗑️ Xóa", new Color(255,59,48)); // 🔴 nút đỏ

        p.add(btnAdd);
        p.add(btnEdit);
        p.add(btnDelete);
        add(p, BorderLayout.SOUTH);

        // 🎯 Sự kiện
        btnAdd.addActionListener(e -> openProductDialog(null));
        btnEdit.addActionListener(e -> {
            int r = tbl.getSelectedRow();
            if (r < 0) { JOptionPane.showMessageDialog(this, "Chọn sản phẩm"); return; }
            int id = (int) model.getValueAt(r, 0);
            openProductDialog(id);
        });
        btnDelete.addActionListener(e -> deleteProduct());

        // 🎨 Table style
        tbl.setGridColor(new Color(220, 220, 220));
        tbl.setRowHeight(28);
        tbl.setFont(new Font("San Francisco", Font.PLAIN, 13));

        // 🎯 Header trắng, chữ đen
        JTableHeader header = tbl.getTableHeader();
        header.setFont(new Font("San Francisco", Font.BOLD, 14));
        header.setBackground(Color.WHITE);
        header.setForeground(Color.BLACK);
        header.setOpaque(true);

        tbl.setShowGrid(true);

        // 🎨 Zebra row
        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(240,248,255));
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(new Color(0,122,255));
                    c.setForeground(Color.WHITE);
                }
                return c;
            }
        });

        load();
    }

    // 🎨 Button có màu tùy chỉnh
    private JButton macButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("San Francisco", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(120, 32));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Color hover = bg.darker();
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { btn.setBackground(hover); }
            public void mouseExited(java.awt.event.MouseEvent evt) { btn.setBackground(bg); }
        });
        return btn;
    }

    // load DB + filter
    public void load() { load(""); }
    public void load(String keyword) {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection();
             PreparedStatement ps = keyword.isEmpty()
                     ? c.prepareStatement("SELECT id,name,price,stock FROM dbo.Products")
                     : c.prepareStatement("SELECT id,name,price,stock FROM dbo.Products WHERE name LIKE ?")) {
            if (!keyword.isEmpty()) ps.setString(1, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getBigDecimal(3), rs.getInt(4)});
            }
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }

    // 🆕 Dialog thêm/sửa
    private void openProductDialog(Integer id) {
        JDialog dlg = new JDialog((Frame)null, id==null?"➕ Thêm sản phẩm":"✏️ Sửa sản phẩm", true);
        dlg.setSize(350,250);
        dlg.setLayout(new BorderLayout(10,10));
        dlg.setLocationRelativeTo(this);

        JPanel form = new JPanel(new GridLayout(3,2,10,10));
        form.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        JTextField tfName = new JTextField();
        JTextField tfPrice = new JTextField();
        JTextField tfQty = new JTextField();

        form.add(new JLabel("Tên:")); form.add(tfName);
        form.add(new JLabel("Giá:")); form.add(tfPrice);
        form.add(new JLabel("Số lượng:")); form.add(tfQty);

        if (id!=null) {
            int r = tbl.getSelectedRow();
            tfName.setText(model.getValueAt(r,1).toString());
            tfPrice.setText(model.getValueAt(r,2).toString());
            tfQty.setText(model.getValueAt(r,3).toString());
        }

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,10));
        JButton btnOk = macButton("Xác nhận", new Color(0,122,255));
        JButton btnCancel = macButton("Hủy", new Color(142,142,147));

        btns.add(btnCancel); btns.add(btnOk);

        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);

        btnOk.addActionListener(e -> {
            try (Connection c = DBHelper.getConnection()) {
                if (id==null) {
                    PreparedStatement ps = c.prepareStatement("INSERT INTO dbo.Products(name,price,stock) VALUES(?,?,?)");
                    ps.setString(1, tfName.getText());
                    ps.setBigDecimal(2, new java.math.BigDecimal(tfPrice.getText()));
                    ps.setInt(3, Integer.parseInt(tfQty.getText()));
                    ps.executeUpdate();
                } else {
                    PreparedStatement ps = c.prepareStatement("UPDATE dbo.Products SET name=?,price=?,stock=? WHERE id=?");
                    ps.setString(1, tfName.getText());
                    ps.setBigDecimal(2, new java.math.BigDecimal(tfPrice.getText()));
                    ps.setInt(3, Integer.parseInt(tfQty.getText()));
                    ps.setInt(4, id);
                    ps.executeUpdate();
                }
                load();
                dlg.dispose();
            } catch(Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Lỗi: "+ex.getMessage());
            }
        });

        btnCancel.addActionListener(e -> dlg.dispose());

        dlg.setVisible(true);
    }

    // Xóa
    private void deleteProduct() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn sản phẩm"); return; }
        int id = (int) model.getValueAt(r,0);
        if (JOptionPane.showConfirmDialog(this, "Xác nhận xóa?")!=0) return;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.Products WHERE id=?")) {
            ps.setInt(1,id); ps.executeUpdate(); load(tfSearch.getText().trim());
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage()); }
    }
}


//___________________________________khách hàng thân thiết______________________________________________________
// ---------------------- Customers Panel ----------------------
//___________________________________khách hàng thân thiết______________________________________________________
// ---------------------- Customers Panel ----------------------
//___________________________________khách hàng thân thiết______________________________________________________
// ---------------------- Customers Panel ----------------------
class CustomersPanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Tên","Phone","Thân thiết"},0);
    private JTable tbl = new JTable(model);
    private JTextField txtSearch = new JTextField();

    public CustomersPanel() {
        setLayout(new BorderLayout());

        // 🎯 Thanh tìm kiếm
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,10,10));
        JLabel lblSearch = new JLabel("🔍 Tìm:");
        lblSearch.setFont(new Font("San Francisco", Font.BOLD, 16)); // chữ to + đậm
        top.add(lblSearch);

        txtSearch.setFont(new Font("San Francisco", Font.PLAIN, 15));
        txtSearch.setPreferredSize(new Dimension(220, 32)); // ô rộng hơn
        top.add(txtSearch);

        JButton btnSearch = macButton("Tìm", new Color(0,122,255));
        btnSearch.setFont(new Font("San Francisco", Font.BOLD, 15));
        btnSearch.setPreferredSize(new Dimension(100, 36));
        btnSearch.addActionListener(e -> searchCustomer());
        top.add(btnSearch);

        add(top, BorderLayout.NORTH);

        // 🎯 Bảng
        tbl.setRowHeight(28);
        tbl.setFont(new Font("San Francisco", Font.PLAIN, 14));
        tbl.getTableHeader().setFont(new Font("San Francisco", Font.BOLD, 14));
        tbl.getTableHeader().setBackground(new Color(230, 240, 250));
        tbl.getTableHeader().setForeground(Color.BLACK);

        // Căn giữa chữ
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i=0;i<tbl.getColumnCount();i++) {
            tbl.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        add(new JScrollPane(tbl), BorderLayout.CENTER);

        // 🎯 Nút chức năng
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton add = macButton("➕ Thêm", new Color(0,122,255));
        JButton edit = macButton("✏️ Sửa", new Color(0,122,255));
        JButton del = macButton("🗑️ Xóa", new Color(220,53,69)); // đỏ

        p.add(add); p.add(edit); p.add(del);
        add(p, BorderLayout.SOUTH);

        add.addActionListener(e -> addCustomer());
        edit.addActionListener(e -> editCustomer());
        del.addActionListener(e -> deleteCustomer());

        load();
    }

    // Nút kiểu macOS
    private JButton macButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("San Francisco", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(120, 36));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(color.darker());
                btn.setFont(btn.getFont().deriveFont(Font.BOLD, 15f));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(color);
                btn.setFont(new Font("San Francisco", Font.PLAIN, 14));
            }
        });
        return btn;
    }

    void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); 
             Statement st = c.createStatement(); 
             ResultSet rs = st.executeQuery("SELECT id,name,phone,isLoyal FROM dbo.Customers")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4) ? "✔" : "✖"
                });
            }
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }

    private void searchCustomer() {
        String keyword = txtSearch.getText().trim();
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); 
             PreparedStatement ps = c.prepareStatement(
                "SELECT id,name,phone,isLoyal FROM dbo.Customers WHERE name LIKE ? OR phone LIKE ?")) {
            ps.setString(1, "%"+keyword+"%");
            ps.setString(2, "%"+keyword+"%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4) ? "✔" : "✖"
                });
            }
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }

    private void addCustomer() {
        String name = JOptionPane.showInputDialog(this, "Tên:"); if (name==null) return;
        String phone = JOptionPane.showInputDialog(this, "Phone:"); if (phone==null) return;
        int loyal = JOptionPane.showConfirmDialog(this, "Là khách thân thiết?", "", JOptionPane.YES_NO_OPTION)==0?1:0;
        try (Connection c = DBHelper.getConnection(); 
             PreparedStatement ps = c.prepareStatement("INSERT INTO dbo.Customers(name,phone,isLoyal) VALUES(?,?,?)")) {
            ps.setString(1,name); ps.setString(2,phone); ps.setBoolean(3, loyal==1); 
            ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }

    private void editCustomer() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn khách"); return; }
        int id = (int) model.getValueAt(r,0);
        String name = JOptionPane.showInputDialog(this, "Tên:", model.getValueAt(r,1)); if (name==null) return;
        String phone = JOptionPane.showInputDialog(this, "Phone:", model.getValueAt(r,2)); if (phone==null) return;
        int loyal = JOptionPane.showConfirmDialog(this, "Là khách thân thiết?", "", JOptionPane.YES_NO_OPTION)==0?1:0;
        try (Connection c = DBHelper.getConnection(); 
             PreparedStatement ps = c.prepareStatement("UPDATE dbo.Customers SET name=?,phone=?,isLoyal=? WHERE id=?")) {
            ps.setString(1,name); ps.setString(2,phone); ps.setBoolean(3,loyal==1); ps.setInt(4,id); 
            ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }

    private void deleteCustomer() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn khách"); return; }
        int id = (int) model.getValueAt(r,0);
        try (Connection c = DBHelper.getConnection(); 
             PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.Customers WHERE id=?")) {
            ps.setInt(1,id); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
    }
}




///____________________________ quản lí tài khoản ___________________________________________
// ---------------------- Accounts Panel ----------------------
// ---------------------- Accounts Panel ----------------------
class AccountsPanel extends JPanel {
    private DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Tài khoản","Vai trò"},0);
    private JTable tbl = new JTable(model);

    public AccountsPanel() {
        setLayout(new BorderLayout()); 
        add(new JScrollPane(tbl), BorderLayout.CENTER);

        // Panel nút chức năng
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        p.setBackground(new Color(245, 245, 247));

        JButton add = macButton("➕ Thêm");
        JButton edit = macButton("✏️ Sửa");
        JButton del = macButton("🗑️ Xóa");

        p.add(add); 
        p.add(edit); 
        p.add(del); 
        add(p, BorderLayout.SOUTH);

        add.addActionListener(e -> addAccount()); 
        edit.addActionListener(e -> editAccount()); 
        del.addActionListener(e -> deleteAccount()); 

        // 🎨 Style bảng
        tbl.setRowHeight(28);
        tbl.setFont(new Font("San Francisco", Font.PLAIN, 14));
        tbl.getTableHeader().setFont(new Font("San Francisco", Font.BOLD, 14));
        tbl.getTableHeader().setBackground(new Color(230, 240, 250));
        tbl.getTableHeader().setForeground(Color.BLACK);

        // Zebra stripe + hover
        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private Color evenColor = Color.WHITE;
            private Color oddColor = new Color(245, 250, 255);
            private Color hoverColor = new Color(220, 240, 255);
            private int hoverRow = -1;

            {
                tbl.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                    public void mouseMoved(java.awt.event.MouseEvent e) {
                        hoverRow = tbl.rowAtPoint(e.getPoint());
                        tbl.repaint();
                    }
                });
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (isSelected) {
                    c.setBackground(new Color(0, 122, 255));
                    c.setForeground(Color.WHITE);
                } else if (row == hoverRow) {
                    c.setBackground(hoverColor);
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(row % 2 == 0 ? evenColor : oddColor);
                    c.setForeground(Color.BLACK);
                }
                ((DefaultTableCellRenderer)c).setHorizontalAlignment(CENTER);
                return c;
            }
        });

        load();
    }

    // 🎨 Button kiểu macOS
    private JButton macButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(new Color(0, 122, 255));
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("San Francisco", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(120, 36));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hover effect
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(10, 132, 255));
                btn.setFont(btn.getFont().deriveFont(Font.BOLD, 15f));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(0, 122, 255));
                btn.setFont(new Font("San Francisco", Font.PLAIN, 14));
            }
        });
        return btn;
    }

    public void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); Statement st = c.createStatement(); 
             ResultSet rs = st.executeQuery("SELECT id,username,role FROM dbo.Users")) {
            while (rs.next()) 
                model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3)});
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }

    // 🆕 Form thêm/sửa chung
    // 🆕 Form thêm/sửa chung - đẹp hơn
private void showAccountForm(Integer id, String username, String role) {
    JDialog dialog = new JDialog((Frame)SwingUtilities.getWindowAncestor(this),
                                 id == null ? "Thêm tài khoản" : "Sửa tài khoản", true);
    dialog.setSize(420, 300);
    dialog.setLocationRelativeTo(this);
    dialog.setLayout(new BorderLayout());
    dialog.getContentPane().setBackground(Color.WHITE);

    // 🏷️ Tiêu đề
    JLabel lblTitle = new JLabel(id == null ? "➕ Thêm tài khoản" : "✏️ Sửa tài khoản", SwingConstants.CENTER);
    lblTitle.setFont(new Font("San Francisco", Font.BOLD, 20));
    lblTitle.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));
    dialog.add(lblTitle, BorderLayout.NORTH);

    // 📋 Panel form
    JPanel formPanel = new JPanel(new GridBagLayout());
    formPanel.setBackground(Color.WHITE);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.anchor = GridBagConstraints.WEST;

    JLabel lblUser = new JLabel("👤 Tên tài khoản:");
    JTextField txtUser = new JTextField(18);
    if (username != null) txtUser.setText(username);

    JLabel lblPass = new JLabel("🔑 Mật khẩu:");
    JPasswordField txtPass = new JPasswordField(18);

    JLabel lblRole = new JLabel("⚙️ Vai trò:");
    JComboBox<String> cboRole = new JComboBox<>(new String[]{"ADMIN","ORDER","WAREHOUSE"});
    if (role != null) cboRole.setSelectedItem(role);

    // 🎨 Style input
    txtUser.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(200,200,200), 1, true),
        BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    txtPass.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(200,200,200), 1, true),
        BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    cboRole.setBorder(BorderFactory.createLineBorder(new Color(200,200,200), 1, true));

    gbc.gridx=0; gbc.gridy=0; formPanel.add(lblUser, gbc);
    gbc.gridx=1; formPanel.add(txtUser, gbc);
    gbc.gridx=0; gbc.gridy=1; formPanel.add(lblPass, gbc);
    gbc.gridx=1; formPanel.add(txtPass, gbc);
    gbc.gridx=0; gbc.gridy=2; formPanel.add(lblRole, gbc);
    gbc.gridx=1; formPanel.add(cboRole, gbc);

    dialog.add(formPanel, BorderLayout.CENTER);

    // 🔘 Nút bấm
    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 12));
    btnPanel.setBackground(Color.WHITE);

    JButton btnOk = new JButton("✅ Xác nhận");
    JButton btnCancel = new JButton("❌ Hủy");

    // Style nút
    btnOk.setBackground(new Color(0, 122, 255));
    btnOk.setForeground(Color.WHITE);
    btnOk.setFont(new Font("San Francisco", Font.BOLD, 14));
    btnOk.setFocusPainted(false);
    btnOk.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));

    btnCancel.setBackground(new Color(240, 240, 240));
    btnCancel.setForeground(Color.BLACK);
    btnCancel.setFont(new Font("San Francisco", Font.PLAIN, 14));
    btnCancel.setFocusPainted(false);
    btnCancel.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));

    // Hover effect
    btnOk.addMouseListener(new java.awt.event.MouseAdapter() {
        public void mouseEntered(java.awt.event.MouseEvent evt) { btnOk.setBackground(new Color(10, 132, 255)); }
        public void mouseExited(java.awt.event.MouseEvent evt) { btnOk.setBackground(new Color(0, 122, 255)); }
    });
    btnCancel.addMouseListener(new java.awt.event.MouseAdapter() {
        public void mouseEntered(java.awt.event.MouseEvent evt) { btnCancel.setBackground(new Color(220, 220, 220)); }
        public void mouseExited(java.awt.event.MouseEvent evt) { btnCancel.setBackground(new Color(240, 240, 240)); }
    });

    btnPanel.add(btnOk);
    btnPanel.add(btnCancel);
    dialog.add(btnPanel, BorderLayout.SOUTH);

    // 🎯 Action
    btnOk.addActionListener(e -> {
        try (Connection c = DBHelper.getConnection()) {
            if (id == null) { // Thêm
                PreparedStatement ps = c.prepareStatement("INSERT INTO dbo.Users(username,password,role) VALUES(?,?,?)");
                ps.setString(1, txtUser.getText());
                ps.setString(2, new String(txtPass.getPassword()));
                ps.setString(3, cboRole.getSelectedItem().toString());
                ps.executeUpdate();
            } else { // Sửa
                if (txtPass.getPassword().length > 0) {
                    PreparedStatement ps = c.prepareStatement("UPDATE dbo.Users SET username=?, password=?, role=? WHERE id=?");
                    ps.setString(1, txtUser.getText());
                    ps.setString(2, new String(txtPass.getPassword()));
                    ps.setString(3, cboRole.getSelectedItem().toString());
                    ps.setInt(4, id);
                    ps.executeUpdate();
                } else {
                    PreparedStatement ps = c.prepareStatement("UPDATE dbo.Users SET username=?, role=? WHERE id=?");
                    ps.setString(1, txtUser.getText());
                    ps.setString(2, cboRole.getSelectedItem().toString());
                    ps.setInt(3, id);
                    ps.executeUpdate();
                }
            }
            load();
            dialog.dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialog, "Lỗi: " + ex.getMessage());
        }
    });

    btnCancel.addActionListener(e -> dialog.dispose());

    dialog.setVisible(true);
}
    
    private void addAccount() {
        showAccountForm(null, null, null);
    }

    private void editAccount() {
        int r = tbl.getSelectedRow(); 
        if (r<0) { JOptionPane.showMessageDialog(this, "Chọn tài khoản"); return; }
        int id = (int) model.getValueAt(r,0);
        String user = (String) model.getValueAt(r,1);
        String role = (String) model.getValueAt(r,2);
        showAccountForm(id, user, role);
    }

    private void deleteAccount() {
        int r = tbl.getSelectedRow(); if (r<0) { JOptionPane.showMessageDialog(this, "Chọn tài khoản"); return; }
        int id = (int) model.getValueAt(r,0);
        try (Connection c = DBHelper.getConnection(); 
             PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.Users WHERE id=?")) {
            ps.setInt(1,id); ps.executeUpdate(); load();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi: "+ex.getMessage()); }
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