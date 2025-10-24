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
import java.util.List;
import java.util.ArrayList;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.RowFilter;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
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
            "IF OBJECT_ID('dbo.Users','U') IS NULL "
            + "CREATE TABLE dbo.Users("
            + "id INT IDENTITY(1,1) PRIMARY KEY, "
            + "username NVARCHAR(50) UNIQUE, "
            + "password NVARCHAR(100), "
            + "role NVARCHAR(20));",
            // Bảng Products
            "IF OBJECT_ID('dbo.Products','U') IS NULL "
            + "CREATE TABLE dbo.Products("
            + "id INT IDENTITY(1,1) PRIMARY KEY, "
            + "name NVARCHAR(100), "
            + "price DECIMAL(18,2), "
            + "stock INT, "
            + "img NVARCHAR(300));",
            // Bảng Customers
            "IF OBJECT_ID('dbo.Customers','U') IS NULL "
            + "CREATE TABLE dbo.Customers("
            + "id INT IDENTITY(1,1) PRIMARY KEY, "
            + "name NVARCHAR(100), "
            + "phone NVARCHAR(20), "
            + "isLoyal BIT);",
            // Bảng Orders (thêm created_by)
            "IF OBJECT_ID('dbo.Orders','U') IS NULL "
            + "CREATE TABLE dbo.Orders("
            + "id INT IDENTITY(1,1) PRIMARY KEY, "
            + "customer_id INT NULL REFERENCES dbo.Customers(id), "
            + "total DECIMAL(18,2) NOT NULL, "
            + "created_at DATETIME DEFAULT GETDATE(), "
            + "created_by INT NULL REFERENCES dbo.Users(id));",
            // Bảng OrderItems
            "IF OBJECT_ID('dbo.OrderItems','U') IS NULL "
            + "CREATE TABLE dbo.OrderItems("
            + "id INT IDENTITY(1,1) PRIMARY KEY, "
            + "orderId INT, "
            + "productId INT, "
            + "stock INT, "
            + "price DECIMAL(18,2));"
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
                if (alpha >= 1f) {
                    ((Timer) e.getSource()).stop();
                }
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
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        rightPanel.add(title, c);

        // Username
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridx = 0;
        JLabel lblUser = new JLabel("Tài khoản:");
        lblUser.setFont(new Font("Segoe UI", Font.PLAIN, 18)); // chữ to hơn
        rightPanel.add(lblUser, c);

        c.gridx = 1;
        styleField(tfUser);
        rightPanel.add(tfUser, c);

        // Password
        c.gridy = 2;
        c.gridx = 0;
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

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
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
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM dbo.Users")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO dbo.Users(username,password,role) VALUES(?,?,?)")) {
                    ins.setString(1, "admin");
                    ins.setString(2, "admin");
                    ins.setString(3, "ADMIN");
                    ins.executeUpdate();
                    ins.setString(1, "staff");
                    ins.setString(2, "staff");
                    ins.setString(3, "ORDER");
                    ins.executeUpdate();
                    ins.setString(1, "warehouse");
                    ins.setString(2, "warehouse");
                    ins.setString(3, "WAREHOUSE");
                    ins.executeUpdate();
                }
            }
        } catch (Exception ex) {
            System.err.println("Seed user failed: " + ex.getMessage());
        }
    }

    private void login() {
        String user = tfUser.getText().trim();
        String pass = new String(pfPass.getPassword());
        if (user.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nhập tài khoản");
            return;
        }
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT id,role FROM dbo.Users WHERE username=? AND password=?")) {
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

    public String username;
    private String role;
    private int userId;
    private CardLayout cardLayout = new CardLayout();
    private JPanel centerPanel;
    // === Getter cho username để các panel khác dùng ===
    private static String currentUsername;

    public static String getUsername() {
        return currentUsername;
    }

    // panels
    private OrderPanel orderPanel;
    private InventoryPanel inventoryPanel;
    private ProductsPanel productsPanel;
    private CustomersPanel customersPanel;
    private AccountsPanel accountsPanel;
    private RevenuePanel revenuePanel;
    private EmployeesPanel employeesPanel;
//    private ProductsDetailPanel productsDetailPanel;
    private JButton activeButton = null;

    public MainFrame(String username, String role, int userId) {
        this.username = username;
        this.role = role;
        this.userId = userId;

        currentUsername = username;
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
                new ImageIcon(getClass().getResource("/icon1/home.png")),
                new Color(102, 178, 255), new Color(51, 153, 255));
        left.add(btnTrangChu);
        left.add(Box.createVerticalStrut(6));

        // ====== Menu chính ======
        JPanel menuMain = new JPanel();
        menuMain.setLayout(new BoxLayout(menuMain, BoxLayout.Y_AXIS));
        menuMain.setOpaque(false);

        JButton btnHome = themedButton(" Order",
                new ImageIcon(getClass().getResource("/icon1/banhang.png")),
                new Color(102, 178, 255), new Color(51, 153, 255));

        JButton btnRevenue = themedButton(" Doanh thu",
                new ImageIcon(getClass().getResource("/icon1/doanhthu.png")),
                new Color(102, 178, 255), new Color(51, 153, 255));

        JButton btnInventory = themedButton(" xuất kho",
                new ImageIcon(getClass().getResource("/icon1/xuatkho.png")),
                new Color(102, 178, 255), new Color(51, 153, 255));

        JButton btnProducts = themedButton(" Danh mục sản phẩm",
                new ImageIcon(getClass().getResource("/icon1/danhmucsp.png")),
                new Color(102, 178, 255), new Color(51, 153, 255));

        JButton btnCustomers = themedButton(" Khách hàng thân thiết",
                new ImageIcon(getClass().getResource("/icon1/khachhang.png")),
                new Color(102, 178, 255), new Color(51, 153, 255));

        JButton btnAccounts = themedButton(" Quản lý tài khoản",
                new ImageIcon(getClass().getResource("/icon1/quanlitaikhoan.png")),
                new Color(102, 178, 255), new Color(51, 153, 255));

        JButton btnEmployees = themedButton(" Quản lý nhân viên",
                new ImageIcon(getClass().getResource("/icon1/quanlinhanvien.png")),
                new Color(102, 178, 255), new Color(51, 153, 255));

        // 🆕 Thêm nút quản lý chi tiết sản phẩm
//        JButton btnProductsDetail = themedButton(" Tổng hợp báo giá cạnh tranh ",
//                new ImageIcon(getClass().getResource("/icon1/tonghop.png")),
//                new Color(102, 178, 255), new Color(51, 153, 255));

        JButton btnSetting = themedButton(" Setting",
                new ImageIcon(getClass().getResource("/icon1/caidat.png")),
                new Color(255, 102, 178), new Color(235, 82, 158));

        menuMain.add(btnHome);
        menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnRevenue);
        menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnInventory);
        menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnProducts);
        menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnCustomers);
        menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnAccounts);
        menuMain.add(Box.createVerticalStrut(6));
        menuMain.add(btnEmployees);
        menuMain.add(Box.createVerticalStrut(6));
//        menuMain.add(btnProductsDetail);
        menuMain.add(Box.createVerticalStrut(6)); // 🆕
        menuMain.add(btnSetting);

        left.add(menuMain);

        // ====== Setting menu ======
        JPanel settingMenu = new JPanel();
        settingMenu.setLayout(new BoxLayout(settingMenu, BoxLayout.Y_AXIS));
        settingMenu.setOpaque(false);
        settingMenu.setVisible(false);

        JButton btnRefresh = themedButton(" Refresh",
                new ImageIcon(getClass().getResource("/icon1/load.png")),
                new Color(0, 151, 196), new Color(0, 131, 176));

        JButton btnLogout = themedButton(" Đăng xuất",
                new ImageIcon(getClass().getResource("/icon1/dangxuat.png")),
                new Color(248, 92, 80), new Color(228, 72, 60));

        settingMenu.add(Box.createVerticalStrut(6));
        settingMenu.add(btnRefresh);
        settingMenu.add(Box.createVerticalStrut(6));
        settingMenu.add(btnLogout);
        left.add(settingMenu);

        // ====== Center panels ======
        centerPanel = new JPanel(cardLayout);
        centerPanel.setOpaque(true);
        centerPanel.setBackground(Color.WHITE);

        orderPanel = new OrderPanel();
        inventoryPanel = new InventoryPanel();
        productsPanel = new ProductsPanel();
        customersPanel = new CustomersPanel();
        accountsPanel = new AccountsPanel();
        revenuePanel = new RevenuePanel();
        employeesPanel = new EmployeesPanel();
//        productsDetailPanel = new ProductsDetailPanel(); // 🆕

        centerPanel.add(new TrangChuPanel(), "HOME");
        centerPanel.add(orderPanel, "ORDER");
        centerPanel.add(revenuePanel, "REVENUE");
        centerPanel.add(inventoryPanel, "INVENTORY");
        centerPanel.add(productsPanel, "PRODUCTS");
        centerPanel.add(customersPanel, "CUSTOMERS");
        centerPanel.add(accountsPanel, "ACCOUNTS");
        centerPanel.add(employeesPanel, "EMPLOYEES");
//        centerPanel.add(productsDetailPanel, "PRODUCTSDETAIL"); // 🆕

        getContentPane().add(left, BorderLayout.WEST);
        getContentPane().add(centerPanel, BorderLayout.CENTER);

        // ====== Sự kiện ======
        btnTrangChu.addActionListener(e -> {
            cardLayout.show(centerPanel, "HOME");
            setActiveButton(btnTrangChu); // 🆕
        });

        btnSetting.addActionListener(e -> {
            boolean show = !settingMenu.isVisible();
            settingMenu.setVisible(show);
            left.revalidate();
            left.repaint();
        });

        btnHome.addActionListener(e -> {
            cardLayout.show(centerPanel, "ORDER");
            setActiveButton(btnHome);
        });
        btnRevenue.addActionListener(e -> {
            cardLayout.show(centerPanel, "REVENUE");
            setActiveButton(btnRevenue);
        });
        btnInventory.addActionListener(e -> {
            cardLayout.show(centerPanel, "INVENTORY");
            setActiveButton(btnInventory);
        });
        btnProducts.addActionListener(e -> {
            cardLayout.show(centerPanel, "PRODUCTS");
            setActiveButton(btnProducts);
        });
        btnCustomers.addActionListener(e -> {
            cardLayout.show(centerPanel, "CUSTOMERS");
            setActiveButton(btnCustomers);
        });
        btnAccounts.addActionListener(e -> {
            cardLayout.show(centerPanel, "ACCOUNTS");
            setActiveButton(btnAccounts);
        });
        btnEmployees.addActionListener(e -> {
            cardLayout.show(centerPanel, "EMPLOYEES");
            setActiveButton(btnEmployees);
        });
//        btnProductsDetail.addActionListener(e -> {
//            cardLayout.show(centerPanel, "PRODUCTSDETAIL");
//            setActiveButton(btnProductsDetail);
//        }); // 🆕

      btnRefresh.addActionListener(e -> {
    refreshAll(); // load lại dữ liệu
    JOptionPane.showMessageDialog(this, "Đã refresh dữ liệu!"); // thông báo
});
        btnLogout.addActionListener(e -> {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        });

        // ====== Nút Thoát ======
        left.add(Box.createVerticalGlue());
        JButton btnExit = themedButton("Thoát",
                new ImageIcon(getClass().getResource("/icon1/thoat.png")),
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
                btnProducts, btnCustomers, btnAccounts,
                btnEmployees);

        // 🆕 Khi khởi động chọn mặc định Trang chủ
        setActiveButton(btnTrangChu);
    }

    public void refreshAll() {
        orderPanel.loadProducts();
        inventoryPanel.load();
        productsPanel.load();
        customersPanel.load();
        accountsPanel.load();
        revenuePanel.load("");
       employeesPanel.load();
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

            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (b != activeButton) { // 🆕 tránh reset nút đang active
                    b.setBackground(bgColor);
                    b.setFont(b.getFont().deriveFont(16f)); // trở về bình thường
                }
            }
        });
        return b;
    }

// 🆕 Hàm set nút active
    private void setActiveButton(JButton btn) {
        if (activeButton != null) {
            activeButton.setBackground(new Color(102, 178, 255)); // màu nhạt
            activeButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        }
        activeButton = btn;
        activeButton.setBackground(new Color(51, 153, 255)); // màu đậm
        activeButton.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 10)); // dịch text sang phải
    }

    private void applyRolePermissions(JButton home, JButton revenue, JButton inventory, JButton products, JButton customers, JButton accounts, JButton employees) {
        // ADMIN có toàn quyền
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }

        if ("ORDER".equalsIgnoreCase(role)) {
            inventory.setEnabled(false);
            products.setEnabled(false);
            customers.setEnabled(false);
            accounts.setEnabled(false);
            employees.setEnabled(false);       // 🔒 chỉ ADMIN được
//            productsDetail.setEnabled(false);  // 🔒 chỉ ADMIN được

        } else if ("WAREHOUSE".equalsIgnoreCase(role)) {
            home.setEnabled(false);
            revenue.setEnabled(false);
            products.setEnabled(false);
            customers.setEnabled(false);
            accounts.setEnabled(false);
            employees.setEnabled(false);       // 🔒 chỉ ADMIN được
//            productsDetail.setEnabled(false);  // 🔒 chỉ ADMIN được
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
    private DefaultTableModel productModel = new DefaultTableModel(new String[]{"Mã sản phẩm", "Tên", "Giá", "Tồn kho"}, 0);
    private DefaultTableModel cartModel = new DefaultTableModel(new String[]{"Mã sản phẩm", "Tên", "Giá", "SL", "Tổng"}, 0);
    private JTextField tfCustomer = new JTextField();
    private JTextField tfPhone = new JTextField();
    private JLabel lblTotal = new JLabel("0");
    private JTextField tfSearch = new JTextField();

    public OrderPanel() {
        setLayout(new BorderLayout());
        Font appleFont = new Font("Helvetica Neue", Font.PLAIN, 14);

        // LEFT: Products
        JPanel left = new JPanel(new BorderLayout(10, 10));
        left.setBorder(BorderFactory.createTitledBorder("Danh mục sản phẩm"));

        tblProducts.setModel(productModel);
        JScrollPane spProducts = new JScrollPane(tblProducts);
        left.add(spProducts, BorderLayout.CENTER);

        // Search
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        tfSearch.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        tfSearch.setBorder(new LineBorder(new Color(200, 200, 200), 1, true));
        tfSearch.setForeground(Color.GRAY);
        tfSearch.setText("Tìm kiếm tại đây...");
        tfSearch.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (tfSearch.getText().equals("Tìm kiếm tại đây...")) {
                    tfSearch.setText("");
                    tfSearch.setForeground(Color.BLACK);
                }
            }

            public void focusLost(FocusEvent e) {
                if (tfSearch.getText().isEmpty()) {
                    tfSearch.setForeground(Color.GRAY);
                    tfSearch.setText("Tìm kiếm tại đây...");
                }
            }
        });
        JButton btnSearch = macButton("🔍", new Color(88, 190, 129));
        searchPanel.add(tfSearch, BorderLayout.CENTER);
        searchPanel.add(btnSearch, BorderLayout.EAST);
        left.add(searchPanel, BorderLayout.NORTH);

        JButton btnAdd = macButton("➕ Thêm vào đơn", new Color(88, 190, 129));
        left.add(btnAdd, BorderLayout.SOUTH);

        // RIGHT: Cart
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Giỏ hàng"));
        tblCart.setModel(cartModel);
        right.add(new JScrollPane(tblCart), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(2, 4, 10, 10));
        bottom.setBorder(BorderFactory.createTitledBorder("Thông tin đơn hàng"));

        bottom.add(new JLabel("Khách:"));
        bottom.add(tfCustomer);
        bottom.add(new JLabel("Phone:"));
        bottom.add(tfPhone);
        bottom.add(new JLabel("Tổng:"));
        bottom.add(lblTotal);

        JButton btnPay = macButton("💳 Thanh toán", new Color(70, 145, 220));
        bottom.add(new JLabel(""));
        bottom.add(btnPay);

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
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterProducts(tfSearch.getText());
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterProducts(tfSearch.getText());
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterProducts(tfSearch.getText());
            }
        });
    }

    // ========== UI HELPERS ==========
    private JButton macButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Helvetica Neue", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setBorder(new EmptyBorder(8, 16, 8, 16));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void styleTable(JTable t, Font f) {
        t.setFont(f);
        t.setRowHeight(28);
        t.getTableHeader().setFont(f.deriveFont(Font.BOLD, 14));
        t.getTableHeader().setBackground(new Color(50, 120, 220));
        t.getTableHeader().setForeground(Color.WHITE);
        t.setShowGrid(true);
        t.setGridColor(new Color(220, 220, 220));
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(Object.class, center);
    }

    // ========== ADD PRODUCT ==========
    private void showAddDialog() {
        int row = tblProducts.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Chọn sản phẩm");
            return;
        }
        int modelRow = tblProducts.convertRowIndexToModel(row);
        int id = (int) productModel.getValueAt(modelRow, 0);
        String name = productModel.getValueAt(modelRow, 1).toString();
        double price = Double.parseDouble(productModel.getValueAt(modelRow, 2).toString());
        int stock = (int) productModel.getValueAt(modelRow, 3);

        String qtyStr = JOptionPane.showInputDialog(this, "Nhập số lượng:", "1");
        if (qtyStr == null) {
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyStr);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Số lượng không hợp lệ");
            return;
        }
        if (qty <= 0 || qty > stock) {
            JOptionPane.showMessageDialog(this, "SL không hợp lệ");
            return;
        }

        cartModel.addRow(new Object[]{id, name, price, qty, price * qty});
        recalcTotal();
    }

    // ========== CONFIRM ORDER + TÍCH/ĐỔI ĐIỂM ==========
// ========== CONFIRM ORDER + TÍCH/ĐỔI ĐIỂM + SỬA/XÓA + CẬP NHẬT GIỎ ==========
    private void showConfirmDialog() {
        if (cartModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Giỏ rỗng");
            return;
        }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Xác nhận thanh toán", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(750, 500);
        dialog.setLocationRelativeTo(this);

        // ======= THÔNG TIN KHÁCH HÀNG =======
        JPanel customerPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        customerPanel.setBorder(BorderFactory.createTitledBorder("Khách hàng"));
        customerPanel.add(new JLabel("Tên KH:"));
        JTextField tfName = new JTextField(tfCustomer.getText());
        customerPanel.add(tfName);
        customerPanel.add(new JLabel("SĐT:"));
        JTextField tfPhone2 = new JTextField(tfPhone.getText());
        customerPanel.add(tfPhone2);

        // ======= BẢNG CHI TIẾT ĐƠN HÀNG =======
        JTable tblConfirm = new JTable(new DefaultTableModel(
                new String[]{"Tên", "SL", "Giá", "Tổng"}, 0
        ));
        DefaultTableModel confirmModel = (DefaultTableModel) tblConfirm.getModel();

        for (int i = 0; i < cartModel.getRowCount(); i++) {
            confirmModel.addRow(new Object[]{
                cartModel.getValueAt(i, 1),
                cartModel.getValueAt(i, 3),
                Double.parseDouble(cartModel.getValueAt(i, 2).toString().replace(",", "")),
                Double.parseDouble(cartModel.getValueAt(i, 4).toString().replace(",", ""))
            });
        }

        DefaultTableCellRenderer currencyRenderer = new DefaultTableCellRenderer() {
            @Override
            public void setValue(Object value) {
                if (value instanceof Number) {
                    setText(String.format("%,.2f", value));
                } else {
                    super.setValue(value);
                }
            }
        };
        tblConfirm.getColumnModel().getColumn(2).setCellRenderer(currencyRenderer);
        tblConfirm.getColumnModel().getColumn(3).setCellRenderer(currencyRenderer);

        JScrollPane spTable = new JScrollPane(tblConfirm);
        spTable.setBorder(BorderFactory.createTitledBorder("Chi tiết đơn hàng"));

        JLabel lblTotal = new JLabel();
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 15));
        updateConfirmTotal(confirmModel, lblTotal);

        JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        totalPanel.add(new JLabel("Tổng cộng: "));
        totalPanel.add(lblTotal);

        // ======= HÌNH THỨC THANH TOÁN =======
        JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        paymentPanel.setBorder(BorderFactory.createTitledBorder("Thanh toán"));
        JComboBox<String> cbPayment = new JComboBox<>(new String[]{"Tiền mặt", "Chuyển khoản", "Thẻ", "Ví điện tử"});
        paymentPanel.add(new JLabel("Hình thức:"));
        paymentPanel.add(cbPayment);

        // ======= CÁC NÚT CHỨC NĂNG =======
        JButton btnEdit = new JButton("Sửa");
        JButton btnDelete = new JButton("Xóa");
        JButton btnConfirm = new JButton("Xác nhận");
        JButton btnCancel = new JButton("Hủy");

        styleButton(btnEdit, new Color(52, 152, 219));
        styleButton(btnDelete, new Color(231, 76, 60));
        styleButton(btnConfirm, new Color(46, 204, 113));
        styleButton(btnCancel, new Color(149, 165, 166));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.add(btnEdit);
        buttonPanel.add(btnDelete);
        buttonPanel.add(btnCancel);
        buttonPanel.add(btnConfirm);

        // ======= SỰ KIỆN =======
        btnCancel.addActionListener(e -> dialog.dispose());

        btnDelete.addActionListener(e -> {
            int row = tblConfirm.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(dialog, "Chọn sản phẩm cần xóa!");
                return;
            }
            confirmModel.removeRow(row);
            cartModel.removeRow(row);
            updateConfirmTotal(confirmModel, lblTotal);
        });

        btnEdit.addActionListener(e -> {
            int row = tblConfirm.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(dialog, "Chọn sản phẩm cần sửa!");
                return;
            }
            String ten = confirmModel.getValueAt(row, 0).toString();
            int slCu = Integer.parseInt(confirmModel.getValueAt(row, 1).toString());
            String input = JOptionPane.showInputDialog(dialog, "Nhập số lượng mới cho \"" + ten + "\":", slCu);
            if (input == null || input.isEmpty()) {
                return;
            }
            try {
                int slMoi = Integer.parseInt(input);
                if (slMoi <= 0) {
                    JOptionPane.showMessageDialog(dialog, "Số lượng phải lớn hơn 0!");
                    return;
                }
                double gia = (double) confirmModel.getValueAt(row, 2);
                double tong = slMoi * gia;
                confirmModel.setValueAt(slMoi, row, 1);
                confirmModel.setValueAt(tong, row, 3);
                cartModel.setValueAt(slMoi, row, 3);
                cartModel.setValueAt(tong, row, 4);
                updateConfirmTotal(confirmModel, lblTotal);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Số lượng không hợp lệ!");
            }
        });

        // --- Xác nhận thanh toán ---
        btnConfirm.addActionListener(e -> {
            String paymentMethod = cbPayment.getSelectedItem().toString();
            tfCustomer.setText(tfName.getText());
            tfPhone.setText(tfPhone2.getText());

            double total = 0;
            for (int i = 0; i < confirmModel.getRowCount(); i++) {
                total += (double) confirmModel.getValueAt(i, 3);
            }

            String phone = tfPhone2.getText().trim();
            boolean coKhachHang = !phone.isEmpty();

            double diemHienTai = 0;
            if (coKhachHang) {
                try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT points FROM dbo.Customers WHERE phone=?")) {
                    ps.setString(1, phone);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        diemHienTai = rs.getDouble(1);
                    }
                } catch (Exception ex) {
                    System.err.println("Lỗi tải điểm: " + ex.getMessage());
                }
            }

            double diemCong = total / 200000.0;
            double giamGia = 0;
            double diemSuDung = 0;
            boolean daDungDiem = false; // kiểm tra KH dùng điểm

            if (coKhachHang && diemHienTai > 0) {
                int choice = JOptionPane.showOptionDialog(dialog,
                        "Khách hàng có " + String.format("%.2f", diemHienTai) + " điểm.\nTổng hóa đơn: " + String.format("%,.2f VNĐ", total)
                        + "\n1 điểm = 10,000đ. Có muốn sử dụng điểm không?",
                        "Đổi điểm", JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null,
                        new Object[]{"Dùng điểm", "Không dùng"}, "Không dùng");

                if (choice == JOptionPane.YES_OPTION) {
                    daDungDiem = true;
                    giamGia = diemHienTai * 10000;
                    if (giamGia > total) {
                        giamGia = total;
                    }
                    diemSuDung = giamGia / 10000.0;
                    total -= giamGia;
                    diemHienTai -= diemSuDung;
                }
            }

            // chỉ cộng điểm mới nếu KH không dùng điểm
            if (!daDungDiem) {
                diemHienTai += diemCong;
            }

            if (coKhachHang) {
                try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE dbo.Customers SET points=? WHERE phone=?")) {
                    ps.setDouble(1, diemHienTai);
                    ps.setString(2, phone);
                    ps.executeUpdate();
                } catch (Exception ex) {
                    System.err.println("Lỗi cập nhật điểm: " + ex.getMessage());
                }
            }

            if (coKhachHang) {
                JOptionPane.showMessageDialog(dialog,
                        "Giảm giá: " + String.format("%,.2f", giamGia) + "đ\n"
                        + "Điểm cộng thêm: " + (!daDungDiem ? String.format("%.2f", diemCong) : "0") + "\n"
                        + "Điểm hiện tại: " + String.format("%.2f", diemHienTai));
            }

            lblTotal.setText(String.format("%,.2f VNĐ", total));
            payOrder(paymentMethod, total);
            dialog.dispose();
            ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();
        });

        // ======= GHÉP GIAO DIỆN =======
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(spTable, BorderLayout.CENTER);
        centerPanel.add(totalPanel, BorderLayout.SOUTH);

        dialog.add(customerPanel, BorderLayout.NORTH);
        dialog.add(centerPanel, BorderLayout.CENTER);
        dialog.add(paymentPanel, BorderLayout.WEST);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

// ======= HÀM CẬP NHẬT TỔNG =======
    private void updateConfirmTotal(DefaultTableModel model, JLabel lbl) {
        double total = 0;
        for (int i = 0; i < model.getRowCount(); i++) {
            total += (double) model.getValueAt(i, 3);
        }
        lbl.setText(String.format("%,.2f VNĐ", total));
        
    }

    // ========== UTILS ==========
    private void styleButton(JButton btn, Color bg) {
        btn.setFocusPainted(false);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 15, 6, 15));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    ////    private void updateConfirmTotal(DefaultTableModel model, JLabel lblTotal) {
////        double total = 0;
////        for (int i = 0; i < model.getRowCount(); i++) {
////            total += Double.parseDouble(model.getValueAt(i, 3).toString());
////        }
////        lblTotal.setText(String.format("%,.0f VNĐ", total));
//    }

    private void recalcTotal() {
        double t = 0;
        for (int i = 0; i < cartModel.getRowCount(); i++) {
            t += Double.parseDouble(cartModel.getValueAt(i, 4).toString());
        }
        lblTotal.setText(String.format("%.2f", t));
    }

    private void filterProducts(String kw) {
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(productModel);
        tblProducts.setRowSorter(sorter);
        if (kw == null || kw.trim().isEmpty() || kw.equals("Tìm kiếm tại đây...")) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + kw.trim()));
        }
    }

    // ========== PAYMENT ==========
private void payOrder(String paymentMethod, double totalFinal) {
    if (cartModel.getRowCount() == 0) {
        return;
    }

    String customer = tfCustomer.getText().trim();
    String phone = tfPhone.getText().trim();

    try (Connection c = DBHelper.getConnection()) {
        int customerId = -1;

        // ========== KIỂM TRA KHÁCH HÀNG ==========
        if (!customer.isEmpty() && !phone.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM dbo.Customers WHERE phone=?")) {
                ps.setString(1, phone);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    customerId = rs.getInt("id");
                }
            }

            // Nếu chưa có khách thì thêm mới
            if (customerId == -1) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.Customers(name,phone,isLoyal,points) VALUES(?,?,0,0)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, customer);
                    ps.setString(2, phone);
                    ps.executeUpdate();
                    ResultSet gk = ps.getGeneratedKeys();
                    if (gk.next()) {
                        customerId = gk.getInt(1);
                    }
                }
            }
        }

        // ========== BẮT ĐẦU GIAO DỊCH ==========
        c.setAutoCommit(false);

        int orderId;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO dbo.Orders(customer_id,total,created_at,created_by,payment_method) VALUES(?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {

            // customer_id
            if (customerId == -1) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, customerId);
            }

            // total
            ps.setBigDecimal(2, new BigDecimal(totalFinal));

            // created_at (thời gian hiện tại)
            ps.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));

            // created_by (ID người dùng đăng nhập)
            ps.setInt(4, DBHelper.CurrentUser.userId);

            // payment_method
            ps.setString(5, paymentMethod);

            ps.executeUpdate();

            ResultSet gk = ps.getGeneratedKeys();
            gk.next();
            orderId = gk.getInt(1);
        }

        // ========== THÊM ORDER ITEMS ==========
        for (int i = 0; i < cartModel.getRowCount(); i++) {
            int productId = (int) cartModel.getValueAt(i, 0);
            double price = (double) cartModel.getValueAt(i, 2);
            int qty = (int) cartModel.getValueAt(i, 3);

            // Thêm chi tiết đơn hàng
            try (PreparedStatement ps2 = c.prepareStatement(
                    "INSERT INTO dbo.OrderItems(order_id,product_id,price,quantity) VALUES(?,?,?,?)")) {
                ps2.setInt(1, orderId);
                ps2.setInt(2, productId);
                ps2.setBigDecimal(3, new BigDecimal(price));
                ps2.setInt(4, qty);
                ps2.executeUpdate();
            }

            // Giảm tồn kho
            try (PreparedStatement ps3 = c.prepareStatement(
                    "UPDATE dbo.Products SET stock = stock - ? WHERE id=?")) {
                ps3.setInt(1, qty);
                ps3.setInt(2, productId);
                ps3.executeUpdate();
            }
        }

        // ========== HOÀN TẤT ==========
        c.commit();
        JOptionPane.showMessageDialog(this, "💰 Thanh toán thành công!");

        cartModel.setRowCount(0);
        recalcTotal();
        loadProducts();

    } catch (Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, "❌ Lỗi thanh toán: " + ex.getMessage());
    }
}


    public void loadProducts() {
        productModel.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id,name,price,stock FROM dbo.Products")) {
            while (rs.next()) {
                productModel.addRow(new Object[]{
                    rs.getInt(1), rs.getString(2), rs.getBigDecimal(3), rs.getInt(4)
                });
            }
        } catch (Exception ex) {
            System.err.println("Load product err: " + ex.getMessage());
        }
    }
}

class RevenuePanel extends JPanel {

    private DefaultTableModel model = new DefaultTableModel(
            new String[]{"Mã Doanh thu", "Khách", "Phone", "Tổng", "Ngày", "Người lập", "Hình thức"}, 0);
    private JTable tbl = new JTable(model);

    public RevenuePanel() {
        setLayout(new BorderLayout());
        JScrollPane sp = new JScrollPane(tbl);
        add(sp, BorderLayout.CENTER);

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        p.setBackground(new Color(245, 245, 247));

        JTextField tfSearch = new JTextField(15);
        JButton btnSearch = macButton("🔍 Tìm");
        JButton btnByDate = macButton(" doanh thu ngày");
        JButton btnByMonth = macButton(" phân tích báo cáo ");
//        JButton btnExportCSV = macButton("💾 Xuất CSV");
        JButton btnTotal = macButton("💰 Xem tổng");
        JButton btnViewBill = macButton("Xem Bill"); // 🆕 thêm nút

        p.add(new JLabel("Tên khách:"));
        p.add(tfSearch);
        p.add(btnSearch);
        p.add(btnByDate);
        p.add(btnByMonth);
//        p.add(btnExportCSV);
        p.add(btnTotal);
        p.add(btnViewBill); // 🆕 thêm vào giao diện

        add(p, BorderLayout.NORTH);

        // Style bảng
        styleTable(tbl);

        btnSearch.addActionListener(e -> load(tfSearch.getText().trim()));
        btnByDate.addActionListener(e -> filterByDateRange());
        btnByMonth.addActionListener(e -> filterByMonth());
//        btnExportCSV.addActionListener(e -> exportCSV());
        btnTotal.addActionListener(e -> showTotalRevenue());
        btnViewBill.addActionListener(e -> viewBillById()); // 🆕 gán sự kiện

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
        t.getTableHeader().setBackground(new Color(0, 122, 204));
        t.getTableHeader().setForeground(Color.WHITE);
        t.setShowGrid(false);

        // Zebra stripe
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus,
                    int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 245, 245));
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });
    }

    public void load(String q) {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT o.id, c.name AS customerName, c.phone AS customerPhone, "
                + "o.total, o.created_at, u.username AS createdBy, o.payment_method "
                + "FROM dbo.Orders o "
                + "LEFT JOIN dbo.Customers c ON o.customer_id = c.id "
                + "LEFT JOIN dbo.Users u ON o.created_by = u.id "
                + "WHERE c.name LIKE ? ORDER BY o.created_at DESC")) {
            ps.setString(1, "%" + q + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt("id"), rs.getString("customerName"), rs.getString("customerPhone"),
                    rs.getBigDecimal("total"), rs.getTimestamp("created_at"), rs.getString("createdBy"), rs.getString("payment_method")});
            }
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
    }

    // =================== FILTER BY DATE RANGE ===================
    private void filterByDateRange() {
        try {
            String from = JOptionPane.showInputDialog(this, "Từ ngày (yyyy-MM-dd):", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            String to = JOptionPane.showInputDialog(this, "Đến ngày (yyyy-MM-dd):", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            if (from == null || to == null) {
                return;
            }
            model.setRowCount(0);
            try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement(
                    "SELECT o.id, c.name AS customerName, c.phone AS customerPhone, "
                    + "o.total, o.created_at, u.username AS createdBy, o.payment_method "
                    + "FROM dbo.Orders o "
                    + "LEFT JOIN dbo.Customers c ON o.customer_id = c.id "
                    + "LEFT JOIN dbo.Users u ON o.created_by = u.id "
                    + "WHERE CAST(o.created_at AS DATE) BETWEEN ? AND ? ORDER BY o.created_at DESC")) {
                ps.setString(1, from);
                ps.setString(2, to);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getInt("id"), rs.getString("customerName"), rs.getString("customerPhone"),
                        rs.getBigDecimal("total"), rs.getTimestamp("created_at"), rs.getString("createdBy"), rs.getString("payment_method")});
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }

    // =================== FILTER BY MONTH ===================
 private void filterByMonth() {
    try {
        // ==== Nhập khoảng thời gian ====
        JTextField txtFrom = new JTextField(10);
        JTextField txtTo = new JTextField(10);
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        panel.add(new JLabel("Từ ngày (yyyy-MM-dd):"));
        panel.add(txtFrom);
        panel.add(new JLabel("Đến ngày (yyyy-MM-dd):"));
        panel.add(txtTo);

        int result = JOptionPane.showConfirmDialog(this, panel, "Chọn khoảng thời gian", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        String fromStr = txtFrom.getText().trim();
        String toStr = txtTo.getText().trim();
        if (fromStr.isEmpty() || toStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bạn phải nhập đầy đủ từ ngày và đến ngày!");
            return;
        }

        java.sql.Date fromDate = java.sql.Date.valueOf(fromStr);
        java.sql.Date toDate = java.sql.Date.valueOf(toStr);

        // ==== Tạo TabbedPane ====
        JTabbedPane tabs = new JTabbedPane();

        // === 1. Báo cáo theo khách hàng ===
        DefaultTableModel modelCustomer = new DefaultTableModel(
                new String[]{"Khách hàng", "SĐT", "Số hóa đơn", "Doanh thu"}, 0);
        JTable tblCustomer = new JTable(modelCustomer);
        customizeTable(tblCustomer);

        String sqlCustomer = """
            SELECT c.name, c.phone, COUNT(o.id) AS invoices, SUM(o.total) AS revenue
            FROM dbo.Orders o
            LEFT JOIN dbo.Customers c ON o.customer_id = c.id
            WHERE o.created_at BETWEEN ? AND ?
            GROUP BY c.name, c.phone
            ORDER BY revenue DESC
        """;

        BigDecimal totalCustomerRevenue = BigDecimal.ZERO;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement(sqlCustomer)) {
            ps.setDate(1, fromDate);
            ps.setDate(2, toDate);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                BigDecimal revenue = rs.getBigDecimal("revenue");
                totalCustomerRevenue = totalCustomerRevenue.add(revenue != null ? revenue : BigDecimal.ZERO);
                modelCustomer.addRow(new Object[]{
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getInt("invoices"),
                        revenue
                });
            }
            // Thêm tổng doanh thu
            modelCustomer.addRow(new Object[]{"", "", "Tổng doanh thu:", totalCustomerRevenue});
        }

        tabs.addTab("Theo khách hàng", new JScrollPane(tblCustomer));

        // === 2. Báo cáo theo doanh thu món ===
        DefaultTableModel modelItem = new DefaultTableModel(
                new String[]{"Tên món", "Giá bán", "Số lượng", "Thành tiền"}, 0);
        JTable tblItem = new JTable(modelItem);
        customizeTable(tblItem);

        String sqlItem = """
            SELECT p.name, p.price, SUM(od.quantity) AS total_qty, SUM(od.quantity*od.price) AS total_amount
            FROM [dbo].[OrderItems] od
            JOIN dbo.Products p ON od.product_id = p.id
            JOIN dbo.Orders o ON od.order_id = o.id
            WHERE o.created_at BETWEEN ? AND ?
            GROUP BY p.name, p.price
            ORDER BY total_amount DESC
        """;

        BigDecimal totalItemRevenue = BigDecimal.ZERO;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement(sqlItem)) {
            ps.setDate(1, fromDate);
            ps.setDate(2, toDate);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                BigDecimal totalAmount = rs.getBigDecimal("total_amount");
                totalItemRevenue = totalItemRevenue.add(totalAmount != null ? totalAmount : BigDecimal.ZERO);
                modelItem.addRow(new Object[]{
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getInt("total_qty"),
                        totalAmount
                });
            }
            modelItem.addRow(new Object[]{"", "", "Tổng doanh thu:", totalItemRevenue});
        }

        tabs.addTab("Theo doanh thu", new JScrollPane(tblItem));

        // === 3. Báo cáo theo nhân viên ===
        DefaultTableModel modelEmployee = new DefaultTableModel(
                new String[]{"Mã NV", "Username", "Số hóa đơn", "Doanh thu"}, 0);
        JTable tblEmployee = new JTable(modelEmployee);
        customizeTable(tblEmployee);

        String sqlEmployee = """
            SELECT u.id AS emp_id, u.username, COUNT(o.id) AS invoices, SUM(o.total) AS revenue
            FROM dbo.Orders o
            LEFT JOIN dbo.Users u ON o.created_by = u.id
            WHERE o.created_at BETWEEN ? AND ?
            GROUP BY u.id, u.username
            ORDER BY revenue DESC
        """;

        BigDecimal totalEmployeeRevenue = BigDecimal.ZERO;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement(sqlEmployee)) {
            ps.setDate(1, fromDate);
            ps.setDate(2, toDate);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                BigDecimal revenue = rs.getBigDecimal("revenue");
                totalEmployeeRevenue = totalEmployeeRevenue.add(revenue != null ? revenue : BigDecimal.ZERO);
                modelEmployee.addRow(new Object[]{
                        rs.getInt("emp_id"),
                        rs.getString("username"),
                        rs.getInt("invoices"),
                        revenue
                });
            }
            modelEmployee.addRow(new Object[]{"", "", "Tổng doanh thu:", totalEmployeeRevenue});
        }

        tabs.addTab("Theo nhân viên", new JScrollPane(tblEmployee));

        // ==== Hiển thị ====
        tabs.setPreferredSize(new Dimension(1000, 500));
        JOptionPane.showMessageDialog(this, tabs, "Báo cáo nâng cao", JOptionPane.INFORMATION_MESSAGE);

    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        ex.printStackTrace();
    }
}

/**
 * Customize table: font, header, row height, cell alignment
 */
private void customizeTable(JTable table) {
    table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    table.setRowHeight(28);
    JTableHeader header = table.getTableHeader();
    header.setFont(new Font("Segoe UI", Font.BOLD, 14));
    header.setBackground(new Color(0, 123, 255));
    header.setForeground(Color.WHITE);

    table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? new Color(245, 250, 255) : Color.WHITE);
            } else {
                c.setBackground(new Color(173, 216, 230));
            }
            setHorizontalAlignment(SwingConstants.CENTER);
            return c;
        }
    });
}

    
    // 🆕=================== XEM BILL THEO MÃ ===================
    // =================== XEM BILL THEO MÃ DOANH THU ===================
    private void viewBillById() {
        String input = JOptionPane.showInputDialog(this, "Nhập mã doanh thu (Order ID):");
        if (input == null || input.trim().isEmpty()) {
            return;
        }

        try (Connection c = DBHelper.getConnection()) {

            // --- Lấy thông tin hóa đơn + khách hàng + điểm ---
            PreparedStatement psOrder = c.prepareStatement(
                    "SELECT o.id, o.created_at, o.payment_method, o.total, "
                    + "u.username AS createdBy, c.name AS customerName, c.phone AS customerPhone, c.points "
                    + "FROM dbo.Orders o "
                    + "LEFT JOIN dbo.Users u ON o.created_by = u.id "
                    + "LEFT JOIN dbo.Customers c ON o.customer_id = c.id "
                    + "WHERE o.id = ?"
            );
            psOrder.setString(1, input);
            ResultSet rsOrder = psOrder.executeQuery();

            if (!rsOrder.next()) {
                JOptionPane.showMessageDialog(this, "Không tìm thấy hóa đơn với mã " + input);
                return;
            }

            // --- Tính tổng hóa đơn từ OrderItems ---
            PreparedStatement psTotal = c.prepareStatement(
                    "SELECT SUM(oi.price * oi.quantity) AS totalBill "
                    + "FROM dbo.OrderItems oi "
                    + "WHERE oi.order_id = ?"
            );
            psTotal.setString(1, input);
            ResultSet rsTotal = psTotal.executeQuery();

            double totalBill = 0;
            if (rsTotal.next()) {
                totalBill = rsTotal.getDouble("totalBill");
            }
            double totalBi = rsOrder.getDouble("total");
            int poin = rsOrder.getInt("points");
            double finalTotal = Math.max(totalBill - totalBi, 0);

            String info = "<html><h2 style='color:#007AFF;'>Hóa đơn #" + rsOrder.getInt("id") + "</h2>"
                    + "<p><b>Khách hàng:</b> " + rsOrder.getString("customerName") + " (" + rsOrder.getString("customerPhone") + ")</p>"
                    + "<p><b>Thời gian:</b> " + rsOrder.getTimestamp("created_at") + "</p>"
                    + "<p><b>Người lập:</b> " + rsOrder.getString("createdBy") + "</p>"
                    + "<p><b>Hình thức thanh toán:</b> " + rsOrder.getString("payment_method") + "</p>"
                    + "<p><b>Điểm hiện có:</b> " + poin + "</p>"
                    + "<p><b>Tổng trước giảm:</b> " + String.format("%,.0f VNĐ", totalBill) + "</p>"
                    + "<p style='color:red;'><b>Giảm theo điểm:</b> -" + String.format("%,.0f VNĐ", finalTotal) + "</p>"
                    + "<p style='color:green;'><b>Tổng sau giảm:</b> " + String.format("%,.0f VNĐ", totalBi) + "</p></html>";

            // --- Lấy chi tiết sản phẩm ---
            PreparedStatement psItems = c.prepareStatement(
                    "SELECT p.name AS productName, oi.price, oi.quantity, (oi.price * oi.quantity) AS totalPrice "
                    + "FROM dbo.OrderItems oi "
                    + "JOIN dbo.Products p ON oi.product_id = p.id "
                    + "WHERE oi.order_id = ?"
            );
            psItems.setString(1, input);
            ResultSet rsItems = psItems.executeQuery();

            DefaultTableModel itemModel = new DefaultTableModel(
                    new String[]{"Tên món", "Giá", "Số lượng", "Thành tiền", "Giảm giá", "Tổng sau giảm"}, 0);

            while (rsItems.next()) {
                itemModel.addRow(new Object[]{
                    rsItems.getString("productName"),
                    String.format("%,.0f", rsItems.getDouble("price")),
                    rsItems.getInt("quantity"),
                    String.format("%,.0f", rsItems.getDouble("totalPrice")),
                    String.format("%,.0f", finalTotal), // số tiền giảm theo điểm
                    String.format("%,.0f", totalBi) // tổng sau giảm (lấy từ rsOrder)
                });
            }

            JTable table = new JTable(itemModel);
            table.setRowHeight(28);
            table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
            table.getTableHeader().setBackground(new Color(0, 122, 204));
            table.getTableHeader().setForeground(Color.WHITE);

            JScrollPane sp = new JScrollPane(table);
            sp.setPreferredSize(new Dimension(600, 300));

            JLabel lblTotal = new JLabel("TỔNG SAU GIẢM: " + String.format("%,.0f VNĐ", totalBi));
            lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblTotal.setForeground(new Color(0, 128, 0));

            JOptionPane.showMessageDialog(this, new Object[]{info, sp, lblTotal},
                    "Chi tiết hóa đơn #" + input, JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi xem bill: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

private void showTotalRevenue() {
    try (Connection conn = DBHelper.getConnection()) {
        // --- Nhập khoảng thời gian ---
        String fromStr = JOptionPane.showInputDialog(this, "Từ ngày (yyyy-MM-dd):",
                new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        String toStr = JOptionPane.showInputDialog(this, "Đến ngày (yyyy-MM-dd):",
                new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

        if (fromStr == null || toStr == null || fromStr.isEmpty() || toStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "⚠️ Vui lòng nhập đủ ngày bắt đầu và kết thúc!");
            return;
        }

        java.sql.Date fromDate = java.sql.Date.valueOf(fromStr);
        java.sql.Date toDate = java.sql.Date.valueOf(toStr);

        // --- Nhập người lập (username) ---
        String createdBy = JOptionPane.showInputDialog(this,
                "Nhập tên đăng nhập (username) của người lập (bỏ trống để xem tất cả):");

        // --- Truy vấn doanh thu từ Orders ---
        String sqlRevenue = """
            SELECT 
                o.id,
                c.name AS CustomerName,
                c.phone AS Phone,
                u.username AS CreatedBy,
                o.payment_method AS Payment,
                o.total AS Total,
                o.created_at AS CreatedDate
            FROM dbo.Orders o
            LEFT JOIN dbo.Customers c ON o.customer_id = c.id
            LEFT JOIN dbo.Users u ON o.created_by = u.id
            WHERE CAST(o.created_at AS DATE) BETWEEN ? AND ?
        """;

        if (createdBy != null && !createdBy.trim().isEmpty()) {
            sqlRevenue += " AND u.username = ? ";
        }

        sqlRevenue += " ORDER BY o.created_at DESC";

        PreparedStatement ps = conn.prepareStatement(sqlRevenue);
        ps.setDate(1, fromDate);
        ps.setDate(2, toDate);
        if (createdBy != null && !createdBy.trim().isEmpty()) {
            ps.setString(3, createdBy.trim());
        }

        ResultSet rs = ps.executeQuery();

        // --- Hiển thị dữ liệu vào bảng ---
        DefaultTableModel tableModel = new DefaultTableModel(new String[]{
            "Mã đơn", "Khách hàng", "Số điện thoại", "Người lập", "Hình thức", "Ngày tạo", "Doanh thu (VNĐ)"
        }, 0);

        double totalRevenue = 0;
        while (rs.next()) {
            int id = rs.getInt("id");
            String customer = rs.getString("CustomerName");
            String phone = rs.getString("Phone");
            String creator = rs.getString("CreatedBy");
            String pay = rs.getString("Payment");
            Timestamp date = rs.getTimestamp("CreatedDate");
            double total = rs.getDouble("Total");
            totalRevenue += total;

            tableModel.addRow(new Object[]{
                id,
                (customer != null ? customer : "(Khách lẻ)"),
                (phone != null ? phone : ""),
                (creator != null ? creator : "(Không rõ)"),
                (pay != null ? pay : ""),
                new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date),
                String.format("%,.0f", total)
            });
        }

        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.getTableHeader().setBackground(new Color(0, 122, 204));
        table.getTableHeader().setForeground(Color.WHITE);

        // --- Tính doanh thu tháng trước ---
        String sqlPrevMonth = """
            SELECT SUM(o.total) AS PrevRevenue
            FROM dbo.Orders o
            LEFT JOIN dbo.Users u ON o.created_by = u.id
            WHERE MONTH(o.created_at) = MONTH(DATEADD(MONTH, -1, ?))
              AND YEAR(o.created_at) = YEAR(DATEADD(MONTH, -1, ?))
        """;
        if (createdBy != null && !createdBy.trim().isEmpty()) {
            sqlPrevMonth += " AND u.username = ? ";
        }

        PreparedStatement psPrev = conn.prepareStatement(sqlPrevMonth);
        psPrev.setDate(1, fromDate);
        psPrev.setDate(2, fromDate);
        if (createdBy != null && !createdBy.trim().isEmpty()) {
            psPrev.setString(3, createdBy.trim());
        }

        ResultSet rsPrev = psPrev.executeQuery();
        double prevRevenue = 0;
        if (rsPrev.next()) {
            prevRevenue = rsPrev.getDouble("PrevRevenue");
        }

        double diff = totalRevenue - prevRevenue;
        JLabel lblCompare = new JLabel();
        lblCompare.setFont(new Font("San Francisco", Font.BOLD, 15));
        lblCompare.setHorizontalAlignment(SwingConstants.CENTER);

        if (prevRevenue == 0) {
            lblCompare.setText("⚠️ Không có dữ liệu tháng trước để so sánh.");
            lblCompare.setForeground(new Color(180, 130, 0));
        } else if (diff > 0) {
            lblCompare.setText(String.format("📈 Doanh thu tăng %,15.0f VNĐ so với tháng trước.", diff));
            lblCompare.setForeground(new Color(0, 180, 0));
        } else if (diff < 0) {
            lblCompare.setText(String.format("📉 Doanh thu giảm %,15.0f VNĐ so với tháng trước.", Math.abs(diff)));
            lblCompare.setForeground(new Color(200, 0, 0));
        } else {
            lblCompare.setText("💤 Doanh thu bằng tháng trước.");
            lblCompare.setForeground(Color.GRAY);
        }

        JLabel lblTotal = new JLabel("TỔNG DOANH THU: " + String.format("%,.0f VNĐ", totalRevenue));
        lblTotal.setFont(new Font("San Francisco", Font.BOLD, 16));
        lblTotal.setForeground(new Color(0, 128, 0));
        lblTotal.setHorizontalAlignment(SwingConstants.CENTER);

        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(750, 350));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.add(sp, BorderLayout.CENTER);
        panel.add(lblTotal, BorderLayout.SOUTH);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        JLabel title = new JLabel("📊 BÁO CÁO DOANH THU (" + fromStr + " → " + toStr + ")" +
                (createdBy != null && !createdBy.trim().isEmpty() ? " - " + createdBy : " (Tất cả người lập)"));
        title.setFont(new Font("San Francisco", Font.BOLD, 18));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setForeground(new Color(0, 122, 255));
        wrapper.add(title, BorderLayout.NORTH);
        wrapper.add(panel, BorderLayout.CENTER);
        wrapper.add(lblCompare, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(this, wrapper,
                "Báo cáo doanh thu", JOptionPane.INFORMATION_MESSAGE);

    } catch (Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(this,
                "Lỗi khi xem doanh thu: " + e.getMessage(),
                "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
}
}
//____________________kho __________________________________________________________
 class InventoryPanel extends JPanel {

    private DefaultTableModel model = new DefaultTableModel(
            new String[]{"Mã SP", "Tên", "SL", "Nhà CC", "SĐT NCC", "Hạn sử dụng"}, 0);
    private JTable tbl = new JTable(model);
    private JTextField txtSearch = new JTextField(20);

    public InventoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(230, 245, 255));

        // ==== Thanh tìm kiếm ====
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBackground(new Color(230, 245, 255));
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JButton btnSearch = modernButton("Tìm", new Color(0, 123, 255));

        searchPanel.add(new JLabel("Tìm sản phẩm:"));
        searchPanel.add(txtSearch);
        searchPanel.add(btnSearch);
        add(searchPanel, BorderLayout.NORTH);

        // ==== Bảng sản phẩm ====
        tbl.setRowHeight(28);
        tbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tbl.setFillsViewportHeight(true);
        JTableHeader header = tbl.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setBackground(new Color(0, 123, 255));
        header.setForeground(Color.WHITE);

        // Renderer: SL < 30 vàng, hết hạn ≤3 ngày đỏ
        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                int qty = 0;
                java.sql.Date expiryDate = null;
                try {
                    qty = Integer.parseInt(table.getValueAt(row, 2).toString());
                    expiryDate = (java.sql.Date) table.getValueAt(row, 5);
                } catch (Exception e) {}

                Color bg = Color.WHITE;

                if (isSelected) {
                    bg = new Color(173, 216, 230);
                } else {
                    boolean expiredSoon = false;
                    if (expiryDate != null) {
                        long diff = expiryDate.getTime() - System.currentTimeMillis();
                        long days = diff / (1000 * 60 * 60 * 24);
                        if (days <= 3) expiredSoon = true;
                    }
                    if (expiredSoon) {
                        bg = new Color(255, 102, 102); // đỏ nhạt
                    } else if (qty < 30) {
                        bg = new Color(255, 255, 180); // vàng
                    } else {
                        bg = (row % 2 == 0) ? new Color(240, 248, 255) : Color.WHITE;
                    }
                }

                c.setBackground(bg);
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        add(new JScrollPane(tbl), BorderLayout.CENTER);

        // ==== Panel nút ====
        JPanel p = new JPanel();
        p.setBackground(new Color(230, 245, 255));
        JButton btnIn = modernButton("Nhập hàng", new Color(0, 123, 255));
        JButton btnOut = modernButton("Xuất kho", new Color(220, 53, 69));
        JButton btnHistory = modernButton("Lịch sử nhập/xuất", new Color(40, 167, 69));
//        JButton btnFilter = modernButton("Lọc", new Color(255, 193, 7));
        p.add(btnIn);
        p.add(btnOut);
        p.add(btnHistory);
//        p.add(btnFilter);
        add(p, BorderLayout.SOUTH);

        // ==== Sự kiện ====
        btnIn.addActionListener(e -> changeQty(true));
        btnOut.addActionListener(e -> changeQty(false));
        btnSearch.addActionListener(e -> search());
//        txtSearch.addActionListener(e -> search());
        btnHistory.addActionListener(e -> showHistory(null, null));

//        btnFilter.addActionListener(e -> {
//            JTextField txtFrom = new JTextField(10);
//            JTextField txtTo = new JTextField(10);
//            JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
//            panel.add(new JLabel("Từ ngày (yyyy-MM-dd):"));
//            panel.add(txtFrom);
//            panel.add(new JLabel("Đến ngày (yyyy-MM-dd):"));
//            panel.add(txtTo);
//
//            int result = JOptionPane.showConfirmDialog(this, panel, "Chọn khoảng ngày", JOptionPane.OK_CANCEL_OPTION);
//            if (result == JOptionPane.OK_OPTION) {
//                String from = txtFrom.getText().trim();
//                String to = txtTo.getText().trim();
//                if (from.isEmpty() || to.isEmpty()) {
//                    JOptionPane.showMessageDialog(this, "Bạn phải nhập đủ ngày bắt đầu và kết thúc!");
//                    return;
//                }
//                showHistory(from, to);
//            }
//        });

        load();
    }

    // ==== Load sản phẩm ====
    public void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, stock, supplierName, supplierPhone, expiryDate FROM dbo.Products")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("stock"),
                        rs.getString("supplierName"),
                        rs.getString("supplierPhone"),
                        rs.getDate("expiryDate")
                });
            }
        } catch (Exception ex) {
            System.err.println("Lỗi load: " + ex.getMessage());
        }
    }

    // ==== Tìm kiếm ====
    private void search() {
        String keyword = txtSearch.getText().trim();
        model.setRowCount(0);
        String sql = """
            SELECT id, name, stock, supplierName, supplierPhone, expiryDate
            FROM dbo.Products
            WHERE name LIKE ?
        """;
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("stock"),
                        rs.getString("supplierName"),
                        rs.getString("supplierPhone"),
                        rs.getDate("expiryDate")
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi tìm kiếm: " + ex.getMessage());
        }
    }

    // ==== Nhập/Xuất hàng ====
    private void changeQty(boolean isIn) {
        int r = tbl.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Chọn mặt hàng trước!");
            return;
        }
        int id = (int) model.getValueAt(r, 0);
        String actionName = isIn ? "Nhập kho" : "Xuất kho";
        String s = JOptionPane.showInputDialog(this, "Nhập số lượng cần " + (isIn ? "thêm" : "xuất") + ":", "1");
        if (s == null || s.trim().isEmpty()) return;

        int q = Integer.parseInt(s.trim());
        if (q <= 0) {
            JOptionPane.showMessageDialog(this, "Số lượng phải > 0!");
            return;
        }

        try (Connection c = DBHelper.getConnection()) {
            PreparedStatement psGet = c.prepareStatement("SELECT supplierName, supplierPhone, expiryDate FROM dbo.Products WHERE id = ?");
            psGet.setInt(1, id);
            ResultSet rsGet = psGet.executeQuery();
            String ncc = "", sdt = "";
            java.sql.Date hsd = null;
            if (rsGet.next()) {
                ncc = rsGet.getString("supplierName");
                sdt = rsGet.getString("supplierPhone");
                hsd = rsGet.getDate("expiryDate");
            }

            // Cập nhật tồn kho
            PreparedStatement psUpdate = c.prepareStatement("UPDATE dbo.Products SET stock = stock + ? WHERE id = ?");
            psUpdate.setInt(1, isIn ? q : -q);
            psUpdate.setInt(2, id);
            psUpdate.executeUpdate();

            // Lấy tồn mới
            PreparedStatement psStock = c.prepareStatement("SELECT stock FROM dbo.Products WHERE id = ?");
            psStock.setInt(1, id);
            ResultSet rsStock = psStock.executeQuery();
            int newStock = 0;
            if (rsStock.next()) newStock = rsStock.getInt("stock");

            // Ghi lịch sử
            PreparedStatement psInsert = c.prepareStatement("""
                INSERT INTO dbo.StockHistory
                (product_id, action, qty, stock_after, createdBy, time, supplier, supplier_phone, expiry_date)
                VALUES (?, ?, ?, ?, ?, GETDATE(), ?, ?, ?)
            """);
            psInsert.setInt(1, id);
            psInsert.setString(2, actionName);
            psInsert.setInt(3, q);
            psInsert.setInt(4, newStock);
            psInsert.setString(5, MainFrame.getUsername());
            psInsert.setString(6, ncc != null ? ncc : "");
            psInsert.setString(7, sdt != null ? sdt : "");
            if (hsd != null) psInsert.setDate(8, hsd); else psInsert.setNull(8, java.sql.Types.DATE);
            psInsert.executeUpdate();

            JOptionPane.showMessageDialog(this, actionName + " thành công!");
            load();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }

    // ==== Lịch sử nhập/xuất ====
private void showHistory(String from, String to) {
    // ==== Form lọc ngày và người lập ====
    JPanel filterPanel = new JPanel(new GridBagLayout());
    filterPanel.setBackground(Color.WHITE);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(8, 10, 8, 10);
    gbc.anchor = GridBagConstraints.WEST;

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    Date today = new Date();
    Calendar cal = Calendar.getInstance();
    cal.setTime(today);
    cal.add(Calendar.DAY_OF_MONTH, -30);
    Date minDate = cal.getTime();

    // Ngày bắt đầu
    gbc.gridx = 0; gbc.gridy = 0;
    filterPanel.add(new JLabel("Từ ngày (yyyy-MM-dd):"), gbc);
    gbc.gridx = 1;
    JTextField txtFrom = new JTextField(10);
    txtFrom.setText(from != null ? from : sdf.format(today));
    filterPanel.add(txtFrom, gbc);

    // Ngày kết thúc
    gbc.gridx = 0; gbc.gridy = 1;
    filterPanel.add(new JLabel("Đến ngày (yyyy-MM-dd):"), gbc);
    gbc.gridx = 1;
    JTextField txtTo = new JTextField(10);
    txtTo.setText(to != null ? to : sdf.format(today));
    filterPanel.add(txtTo, gbc);

    // Người lập
    gbc.gridx = 0; gbc.gridy = 2;
    filterPanel.add(new JLabel("Người lập (bỏ trống = tất cả):"), gbc);
    gbc.gridx = 1;
    JComboBox<String> cboUser = new JComboBox<>();
    cboUser.addItem(""); // Tất cả
    try (Connection c = DBHelper.getConnection();
         Statement st = c.createStatement();
         ResultSet rs = st.executeQuery("SELECT DISTINCT username FROM dbo.Users")) {
        while (rs.next()) cboUser.addItem(rs.getString(1));
    } catch (Exception ex) {
        System.err.println("Lỗi load users: " + ex.getMessage());
    }
    filterPanel.add(cboUser, gbc);

    int result = JOptionPane.showConfirmDialog(this, filterPanel, "Lọc lịch sử nhập/xuất",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (result != JOptionPane.OK_OPTION) return;

    // ==== Lấy ngày và người lập ====
    java.util.Date fromDate;
    java.util.Date toDate;
    try {
        fromDate = sdf.parse(txtFrom.getText().trim());
        toDate = sdf.parse(txtTo.getText().trim());
    } catch (ParseException ex) {
        JOptionPane.showMessageDialog(this, "Ngày không hợp lệ!");
        return;
    }

    // Kiểm tra giới hạn 30 ngày
    if (fromDate.before(minDate)) fromDate = minDate;
    if (toDate.after(today)) toDate = today;

    String usernameValue = cboUser.getSelectedItem() != null ? cboUser.getSelectedItem().toString() : "";

    // ==== Tạo bảng lịch sử ====
    DefaultTableModel historyModel = new DefaultTableModel(
            new String[]{"Mã SP", "Tên SP", "Hành động", "Số lượng", "Tồn sau",
                    "NCC", "SĐT NCC", "Hạn SD", "Người", "Thời gian"}, 0);
    JTable historyTable = new JTable(historyModel);
    historyTable.setRowHeight(26);

    JTableHeader header = historyTable.getTableHeader();
    header.setFont(new Font("Segoe UI", Font.BOLD, 14));
    header.setBackground(new Color(0, 123, 255));
    header.setForeground(Color.WHITE);

    historyTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (!isSelected) c.setBackground(row % 2 == 0 ? new Color(245, 250, 255) : Color.WHITE);
            else c.setBackground(new Color(173, 216, 230));
            setHorizontalAlignment(SwingConstants.CENTER);

            if (col == 7 && value instanceof java.sql.Date) {
                java.sql.Date expiry = (java.sql.Date) value;
                long diff = expiry.getTime() - System.currentTimeMillis();
                if (diff >= 0 && diff <= 3L * 24 * 3600 * 1000) c.setBackground(new Color(255, 150, 150));
            }
            return c;
        }
    });

    // ==== Load dữ liệu từ DB ====
    String sql = """
            SELECT h.product_id, p.name, h.action, h.qty, h.stock_after,
                   h.supplier, h.supplier_phone, h.expiry_date,
                   h.createdBy, h.time
            FROM dbo.StockHistory h
            JOIN dbo.Products p ON h.product_id = p.id
            WHERE h.time >= ? AND h.time <= ?
              AND (? = '' OR h.createdBy = ?)
            ORDER BY h.time DESC
            """;

    try (Connection c = DBHelper.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setTimestamp(1, new java.sql.Timestamp(fromDate.getTime()));
        ps.setTimestamp(2, new java.sql.Timestamp(toDate.getTime() + 86399000L));
        ps.setString(3, usernameValue);
        ps.setString(4, usernameValue);

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            historyModel.addRow(new Object[]{
                    rs.getInt("product_id"),
                    rs.getString("name"),
                    rs.getString("action"),
                    rs.getInt("qty"),
                    rs.getInt("stock_after"),
                    rs.getString("supplier"),
                    rs.getString("supplier_phone"),
                    rs.getDate("expiry_date"),
                    rs.getString("createdBy"),
                    rs.getTimestamp("time")
            });
        }
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Lỗi load lịch sử: " + ex.getMessage());
        ex.printStackTrace();
    }

    JScrollPane sp = new JScrollPane(historyTable);
    sp.setPreferredSize(new Dimension(950, 450));
    JOptionPane.showMessageDialog(this, sp, "Lịch sử nhập/xuất", JOptionPane.INFORMATION_MESSAGE);
}


    // ==== Button hiện đại ====
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
            public void mouseEntered(java.awt.event.MouseEvent evt) { b.setBackground(baseColor.darker()); }
            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) { b.setBackground(baseColor); }
        });
        return b;
    }
}

///__________________________________________danh mục sản phẩm ________________________________________

// ---------------------- Products Panel ----------------------



class ProductsPanel extends JPanel {

    private DefaultTableModel model = new DefaultTableModel(
            new String[]{"Mã sản phẩm", "Tên", "Giá", "SL", "Nhà cung cấp", "SĐT NCC", "Hạn sử dụng"}, 0);
    private JTable tbl = new JTable(model);
    private JTextField tfSearch = new JTextField(20);

    public ProductsPanel() {
        setLayout(new BorderLayout(10, 10));

        // 🔍 Thanh tìm kiếm
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JLabel lblSearch = new JLabel("🔍 Tìm kiếm:");
        lblSearch.setFont(new Font("San Francisco", Font.BOLD, 16));

        tfSearch.setFont(new Font("San Francisco", Font.PLAIN, 16));
        tfSearch.setPreferredSize(new Dimension(250, 35));

        JButton btnSearch = macButton("Tìm", new Color(0, 122, 255));
        searchPanel.add(lblSearch);
        searchPanel.add(tfSearch);
        searchPanel.add(btnSearch);
        add(searchPanel, BorderLayout.NORTH);
        btnSearch.addActionListener(e -> load(tfSearch.getText().trim()));

        add(new JScrollPane(tbl), BorderLayout.CENTER);

        // 🎨 Toolbar
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        p.setBackground(new Color(245, 245, 247));

        JButton btnAdd = macButton("➕ Thêm", new Color(0, 122, 255));
        JButton btnEdit = macButton("✏️ Sửa", new Color(0, 122, 255));
        JButton btnDelete = macButton("🗑️ Xóa", new Color(255, 59, 48));

        p.add(btnAdd);
        p.add(btnEdit);
        p.add(btnDelete);
        add(p, BorderLayout.SOUTH);

        btnAdd.addActionListener(e -> openProductDialog(null));
        btnEdit.addActionListener(e -> {
            int r = tbl.getSelectedRow();
            if (r < 0) {
                JOptionPane.showMessageDialog(this, "Chọn sản phẩm");
                return;
            }
            int id = (int) model.getValueAt(r, 0);
            openProductDialog(id);
        });
        btnDelete.addActionListener(e -> deleteProduct());

        // 🎨 Table style
        tbl.setGridColor(new Color(220, 220, 220));
        tbl.setRowHeight(28);
        tbl.setFont(new Font("San Francisco", Font.PLAIN, 13));

        JTableHeader header = tbl.getTableHeader();
        header.setFont(new Font("San Francisco", Font.BOLD, 14));
        header.setBackground(new Color(0, 0, 255));
        header.setForeground(Color.white);
        tbl.setShowGrid(true);

        // 🌈 Renderer cảnh báo
        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(240, 248, 255));
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(new Color(0, 122, 255));
                    c.setForeground(Color.WHITE);
                }

                // ⚠️ Cảnh báo hạn sử dụng
                try {
                    String expStr = table.getValueAt(row, 6).toString();
                    if (expStr != null && !expStr.trim().isEmpty()) {
                        LocalDate expiry = LocalDate.parse(expStr, fmt);
                        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), expiry);
                        if (daysLeft < 0) {
                            c.setBackground(new Color(255, 102, 102)); // đỏ
                        } else if (daysLeft <= 7) {
                            c.setBackground(new Color(255, 178, 102)); // cam
                        }
                    }
                } catch (Exception ignored) {
                }

                // ⚠️ Tồn kho thấp
                try {
                    int qty = Integer.parseInt(table.getValueAt(row, 3).toString());
                    if (qty < 30 && !isSelected) {
                        c.setBackground(new Color(255, 255, 180)); // vàng
                    }
                } catch (Exception ignored) {
                }

                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        load();
    }

    // Nút kiểu macOS
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
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(hover);
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(bg);
            }
        });
        return btn;
    }

    // Load DB
    public void load() {
        load("");
    }

    public void load(String keyword) {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = keyword.isEmpty()
                ? c.prepareStatement("SELECT id, name, price, stock, supplierName, supplierPhone, expiryDate FROM dbo.Products")
                : c.prepareStatement("SELECT id, name, price, stock, supplierName, supplierPhone, expiryDate FROM dbo.Products WHERE name LIKE ?")) {
            if (!keyword.isEmpty()) {
                ps.setString(1, "%" + keyword + "%");
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getInt("stock"),
                        rs.getString("supplierName"),
                        rs.getString("supplierPhone"),
                        rs.getDate("expiryDate")
                    });
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi tải dữ liệu: " + ex.getMessage());
        }
    }

    // 🆕 Thêm / Sửa
    private void openProductDialog(Integer id) {
        JDialog dlg = new JDialog((Frame) null, id == null ? "➕ Thêm sản phẩm" : "✏️ Sửa sản phẩm", true);
        dlg.setSize(400, 400);
        dlg.setLayout(new BorderLayout(10, 10));
        dlg.setLocationRelativeTo(this);

        JPanel form = new JPanel(new GridLayout(6, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextField tfName = new JTextField();
        JTextField tfPrice = new JTextField();
        JTextField tfQty = new JTextField();
        JTextField tfSupplier = new JTextField();
        JTextField tfPhone = new JTextField();
        JTextField tfExpiry = new JTextField("yyyy-MM-dd");

        form.add(new JLabel("Tên:"));
        form.add(tfName);
        form.add(new JLabel("Giá:"));
        form.add(tfPrice);
        form.add(new JLabel("Số lượng:"));
        form.add(tfQty);
        form.add(new JLabel("Nhà cung cấp:"));
        form.add(tfSupplier);
        form.add(new JLabel("SĐT NCC:"));
        form.add(tfPhone);
        form.add(new JLabel("Hạn sử dụng:"));
        form.add(tfExpiry);

        if (id != null) {
            int r = tbl.getSelectedRow();
            tfName.setText(model.getValueAt(r, 1).toString());
            tfPrice.setText(model.getValueAt(r, 2).toString());
            tfQty.setText(model.getValueAt(r, 3).toString());
            tfSupplier.setText(model.getValueAt(r, 4).toString());
            tfPhone.setText(model.getValueAt(r, 5).toString());
            tfExpiry.setText(model.getValueAt(r, 6).toString());
        }

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btnOk = macButton("Xác nhận", new Color(0, 122, 255));
        JButton btnCancel = macButton("Hủy", new Color(142, 142, 147));
        btns.add(btnCancel);
        btns.add(btnOk);

        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);

        btnOk.addActionListener(e -> {
            try (Connection c = DBHelper.getConnection()) {
                if (id == null) {
                    PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO dbo.Products(name,price,stock,supplierName,supplierPhone,expiryDate) VALUES(?,?,?,?,?,?)");
                    ps.setString(1, tfName.getText());
                    ps.setBigDecimal(2, new java.math.BigDecimal(tfPrice.getText()));
                    ps.setInt(3, Integer.parseInt(tfQty.getText()));
                    ps.setString(4, tfSupplier.getText());
                    ps.setString(5, tfPhone.getText());
                    ps.setDate(6, java.sql.Date.valueOf(tfExpiry.getText()));
                    ps.executeUpdate();
                } else {
                    PreparedStatement ps = c.prepareStatement(
                            "UPDATE dbo.Products SET name=?,price=?,stock=?,supplierName=?,supplierPhone=?,expiryDate=? WHERE id=?");
                    ps.setString(1, tfName.getText());
                    ps.setBigDecimal(2, new java.math.BigDecimal(tfPrice.getText()));
                    ps.setInt(3, Integer.parseInt(tfQty.getText()));
                    ps.setString(4, tfSupplier.getText());
                    ps.setString(5, tfPhone.getText());
                    ps.setDate(6, java.sql.Date.valueOf(tfExpiry.getText()));
                    ps.setInt(7, id);
                    ps.executeUpdate();
                }
                dlg.dispose();
                load();
                ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();
                
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Lỗi: " + ex.getMessage());
            }
        });

        btnCancel.addActionListener(e -> dlg.dispose());
        dlg.setVisible(true);
    }

    // 🗑️ Xóa
    private void deleteProduct() {
        int r = tbl.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Chọn sản phẩm");
            return;
        }
        int id = (int) model.getValueAt(r, 0);
        if (JOptionPane.showConfirmDialog(this, "Xác nhận xóa?") != 0) {
            return;
        }
        try (Connection c = DBHelper.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.Products WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            model.removeRow(r);
                 // 🔄 Làm mới toàn bộ hệ thống
        ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();
        
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }
}



class CustomersPanel extends JPanel {

    private DefaultTableModel model = new DefaultTableModel(
            new String[]{"Mã KH", "Tên", "Phone", "Điểm tích lũy"}, 0
    );
    private JTable tbl = new JTable(model);
    private JTextField txtSearch = new JTextField();

    public CustomersPanel() {
        setLayout(new BorderLayout());

        // 🔍 Thanh tìm kiếm
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JLabel lblSearch = new JLabel("🔍 Tìm:");
        lblSearch.setFont(new Font("San Francisco", Font.BOLD, 16));
        top.add(lblSearch);

        txtSearch.setFont(new Font("San Francisco", Font.PLAIN, 15));
        txtSearch.setPreferredSize(new Dimension(220, 32));
        top.add(txtSearch);

        JButton btnSearch = macButton("Tìm", new Color(0, 122, 255));
        btnSearch.addActionListener(e -> searchCustomer());
        top.add(btnSearch);

        add(top, BorderLayout.NORTH);

        // 🧾 Bảng
        tbl.setRowHeight(28);
        tbl.setFont(new Font("San Francisco", Font.PLAIN, 14));
        tbl.getTableHeader().setFont(new Font("San Francisco", Font.BOLD, 14));
        tbl.getTableHeader().setBackground(new Color(0, 0, 255));
        tbl.getTableHeader().setForeground(Color.white);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < tbl.getColumnCount(); i++) {
            tbl.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        add(new JScrollPane(tbl), BorderLayout.CENTER);

        // ⚙️ Nút chức năng
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton add = macButton("➕ Thêm", new Color(0, 122, 255));
        JButton edit = macButton("✏️ Sửa", new Color(0, 122, 255));
        JButton del = macButton("🗑️ Xóa", new Color(220, 53, 69));
        JButton redeem = macButton("🎁 Đổi điểm", new Color(40, 167, 69));

        p.add(add);
        p.add(edit);
        p.add(del);
        p.add(redeem);
        add(p, BorderLayout.SOUTH);

        add.addActionListener(e -> addCustomer());
        edit.addActionListener(e -> editCustomer());
        del.addActionListener(e -> deleteCustomer());
        redeem.addActionListener(e -> redeemPoints());

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

    // 🔄 Load dữ liệu
    void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, phone, points FROM Customers")) {
            while (rs.next()) {
                float points = rs.getFloat("points");
                model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        String.format("%.2f", points)
                });
            }
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
    }

    // 🔍 Tìm khách hàng
    private void searchCustomer() {
        String keyword = txtSearch.getText().trim();
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, phone, points FROM Customers WHERE name LIKE ? OR phone LIKE ?")) {
            ps.setString(1, "%" + keyword + "%");
            ps.setString(2, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                float points = rs.getFloat("points");
                model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        String.format("%.2f", points)
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }

    // ✅ Không cho phép thêm khách hàng khi bỏ trống tên hoặc số điện thoại
    private void addCustomer() {
        String name = JOptionPane.showInputDialog(this, "Tên khách hàng:");
        if (name == null) return; // bấm Cancel
        name = name.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "⚠️ Tên khách hàng không được để trống!");
            return;
        }

        String phone = JOptionPane.showInputDialog(this, "Số điện thoại:");
        if (phone == null) return; // bấm Cancel
        phone = phone.trim();
        if (phone.isEmpty()) {
            JOptionPane.showMessageDialog(this, "⚠️ Số điện thoại không được để trống!");
            return;
        }

        try (Connection c = DBHelper.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO Customers(name, phone, points) VALUES (?, ?, 0)")) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.executeUpdate();
            load();
            ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();
            JOptionPane.showMessageDialog(this, "✅ Đã thêm khách hàng thành công!");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }

    private void editCustomer() {
        int r = tbl.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Chọn khách");
            return;
        }
        int id = (int) model.getValueAt(r, 0);
        String name = JOptionPane.showInputDialog(this, "Tên:", model.getValueAt(r, 1));
        if (name == null) {
            return;
        }
        String phone = JOptionPane.showInputDialog(this, "Phone:", model.getValueAt(r, 2));
        if (phone == null) {
            return;
        }

        try (Connection c = DBHelper.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE Customers SET name=?, phone=? WHERE id=?")) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setInt(3, id);
            ps.executeUpdate();
            load();
            ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }

    private void deleteCustomer() {
        int r = tbl.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Chọn khách");
            return;
        }
        int id = (int) model.getValueAt(r, 0);
        try (Connection c = DBHelper.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM Customers WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            load();
            ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
        }
    }

    // 🎁 Đổi điểm -> giảm giá
    private void redeemPoints() {
        int r = tbl.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Chọn khách hàng để đổi điểm!");
            return;
        }
        int id = (int) model.getValueAt(r, 0);

        float points;
        try {
            String strPoints = model.getValueAt(r, 3).toString().replace(",", ".");
            points = Float.parseFloat(strPoints);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi đọc điểm khách hàng!");
            return;
        }

        String input = JOptionPane.showInputDialog(this,
                "Khách có " + String.format("%.2f", points) + " điểm.\nNhập số điểm muốn đổi:");
        if (input == null || input.isEmpty()) {
            return;
        }

        float redeem;
        try {
            redeem = Float.parseFloat(input.replace(",", "."));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số điểm không hợp lệ!");
            return;
        }

        if (redeem > points) {
            JOptionPane.showMessageDialog(this, "Không đủ điểm!");
            return;
        }

        int discountValue = (int) (redeem * 10000);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Đổi " + String.format("%.2f", redeem) + " điểm = " + discountValue + " VND giảm giá?\nXác nhận?",
                "Xác nhận đổi điểm", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection c = DBHelper.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE Customers SET points = points - ? WHERE id=?")) {
                ps.setFloat(1, redeem);
                ps.setInt(2, id);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Đã đổi " + String.format("%.2f", redeem)
                        + " điểm. Giảm " + discountValue + " VND!");
                load();
                ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
            }
        }
    }
}

///____________________________ quản lí tài khoản ___________________________________________
// ---------------------- Accounts Panel ----------------------
// ---------------------- Accounts Panel ----------------------
class AccountsPanel extends JPanel {

    private DefaultTableModel model = new DefaultTableModel(new String[]{"mã tài khoản", "Tài khoản", "Vai trò"}, 0);
    private JTable tbl = new JTable(model);

    public AccountsPanel() {
        setLayout(new BorderLayout());
        add(new JScrollPane(tbl), BorderLayout.CENTER);

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

        tbl.setRowHeight(28);
        tbl.setFont(new Font("San Francisco", Font.PLAIN, 14));
        tbl.getTableHeader().setFont(new Font("San Francisco", Font.BOLD, 14));
        tbl.getTableHeader().setBackground(new Color(0, 0, 255));
        tbl.getTableHeader().setForeground(Color.white);

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
                ((DefaultTableCellRenderer) c).setHorizontalAlignment(CENTER);
                return c;
            }
        });

        load();
    }

    private JButton macButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(new Color(0, 122, 255));
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("San Francisco", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(120, 36));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

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
    model.setRowCount(0); // Xóa tất cả dữ liệu cũ
    try (Connection c = DBHelper.getConnection();
         Statement st = c.createStatement();
         ResultSet rs = st.executeQuery("SELECT id,username,role FROM dbo.Users")) {

        while (rs.next()) {
            model.addRow(new Object[]{rs.getInt("id"), rs.getString("username"), rs.getString("role")});
        }

        tbl.repaint(); // đảm bảo JTable được vẽ lại

    } catch (Exception ex) {
        System.err.println(ex.getMessage());
    }
}

    private void showAccountForm(Integer id, String username, String role) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                id == null ? "Thêm tài khoản" : "Sửa tài khoản", true);
        dialog.setSize(420, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(Color.WHITE);

        JLabel lblTitle = new JLabel(id == null ? "➕ Thêm tài khoản" : "✏️ Sửa tài khoản", SwingConstants.CENTER);
        lblTitle.setFont(new Font("San Francisco", Font.BOLD, 20));
        lblTitle.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));
        dialog.add(lblTitle, BorderLayout.NORTH);

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
        JComboBox<String> cboRole = new JComboBox<>(new String[]{"ADMIN", "ORDER", "WAREHOUSE"});
        if (role != null) cboRole.setSelectedItem(role);

        txtUser.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        txtPass.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        cboRole.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true));

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(lblUser, gbc);
        gbc.gridx = 1;
        formPanel.add(txtUser, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(lblPass, gbc);
        gbc.gridx = 1;
        formPanel.add(txtPass, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(lblRole, gbc);
        gbc.gridx = 1;
        formPanel.add(cboRole, gbc);

        dialog.add(formPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 12));
        btnPanel.setBackground(Color.WHITE);

        JButton btnOk = new JButton("✅ Xác nhận");
        JButton btnCancel = new JButton("❌ Hủy");

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

        btnOk.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btnOk.setBackground(new Color(10, 132, 255));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                btnOk.setBackground(new Color(0, 122, 255));
            }
        });
        btnCancel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btnCancel.setBackground(new Color(220, 220, 220));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                btnCancel.setBackground(new Color(240, 240, 240));
            }
        });

        btnPanel.add(btnOk);
        btnPanel.add(btnCancel);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        btnOk.addActionListener(e -> {
            try (Connection c = DBHelper.getConnection()) {
                if (id == null) { // Thêm
                    PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO dbo.Users(username,password,role) VALUES(?,?,?)");
                    ps.setString(1, txtUser.getText());
                    ps.setString(2, new String(txtPass.getPassword()));
                    ps.setString(3, cboRole.getSelectedItem().toString());
                    ps.executeUpdate();
                } else { // Sửa
                    if (txtPass.getPassword().length > 0) {
                        PreparedStatement ps = c.prepareStatement(
                                "UPDATE dbo.Users SET username=?, password=?, role=? WHERE id=?");
                        ps.setString(1, txtUser.getText());
                        ps.setString(2, new String(txtPass.getPassword()));
                        ps.setString(3, cboRole.getSelectedItem().toString());
                        ps.setInt(4, id);
                        ps.executeUpdate();
                    } else {
                        PreparedStatement ps = c.prepareStatement(
                                "UPDATE dbo.Users SET username=?, role=? WHERE id=?");
                        ps.setString(1, txtUser.getText());
                        ps.setString(2, cboRole.getSelectedItem().toString());
                        ps.setInt(3, id);
                        ps.executeUpdate();
                    }

                    // Đồng bộ username sang Employees
                    PreparedStatement psEmp = c.prepareStatement(
                            "UPDATE Employees SET username=? WHERE user_id=?");
                    psEmp.setString(1, txtUser.getText());
                    psEmp.setInt(2, id);
                    psEmp.executeUpdate();
                }

                load();
                ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();
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
    if (r < 0) {
        JOptionPane.showMessageDialog(this, "Chọn tài khoản");
        return;
    }

    Object idObj = model.getValueAt(r, 0);
    Object userObj = model.getValueAt(r, 1);
    Object roleObj = model.getValueAt(r, 2);

    if (idObj == null) {
        JOptionPane.showMessageDialog(this, "Mã tài khoản trống!");
        return;
    }

    int id;
    try {
        id = Integer.parseInt(idObj.toString());
    } catch (NumberFormatException e) {
        JOptionPane.showMessageDialog(this, "Mã tài khoản không hợp lệ!");
        return;
    }

    String user = (userObj == null) ? "" : userObj.toString();
    String role = (roleObj == null) ? "" : roleObj.toString();

    showAccountForm(id, user, role);
}

private void deleteAccount() {
    int r = tbl.getSelectedRow();
    if (r < 0) {
        JOptionPane.showMessageDialog(this, "Chọn tài khoản");
        return;
    }

    Object idObj = model.getValueAt(r, 0);
    if (idObj == null) {
        JOptionPane.showMessageDialog(this, "Mã tài khoản trống!");
        return;
    }

    int id;
    try {
        id = Integer.parseInt(idObj.toString());
    } catch (NumberFormatException e) {
        JOptionPane.showMessageDialog(this, "Mã tài khoản không hợp lệ!");
        return;
    }

    int confirm = JOptionPane.showConfirmDialog(this,
            "Bạn có chắc muốn xóa tài khoản này?", "Xác nhận", JOptionPane.YES_NO_OPTION);
    if (confirm != JOptionPane.YES_OPTION) return;

    try (Connection c = DBHelper.getConnection()) {
        // Set NULL ở Employees
        PreparedStatement psEmp = c.prepareStatement(
                "UPDATE Employees SET username=NULL, password=NULL, user_id=NULL WHERE user_id=?");
        psEmp.setInt(1, id);
        psEmp.executeUpdate();

        // Xóa User
        PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.Users WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();

        load();
        ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
    }
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
