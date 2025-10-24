import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.sql.*;

public class EmployeesPanel extends JPanel {
    private DefaultTableModel model;
    private JTable tbl;
    private JLabel lblImage;
    private JButton btnAdd, btnEdit, btnDelete, btnChooseImg, btnView;

    // Form nhập liệu
    private JTextField tfName, tfPhone, tfCCCD, tfAddress, tfRole, tfUser, tfSalary;
    private JPasswordField tfPass;

    public EmployeesPanel() {
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);

        // ==== Bảng nhân viên ====
        model = new DefaultTableModel(new String[]{
                "Mã nhân viên ","Tên","SĐT","CCCD","Quê quán","Vị trí","Tài khoản","Mật khẩu","Lương","Ảnh"
        }, 0);
        tbl = new JTable(model);
        tbl.setRowHeight(28);
        tbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // ==== Style cho header ====
        tbl.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        tbl.getTableHeader().setOpaque(false);
        tbl.getTableHeader().setBackground(new Color(0, 120, 215));
        tbl.getTableHeader().setForeground(Color.WHITE);

        // Zebra stripe + hover
        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private Color even = Color.WHITE;
            private Color odd = new Color(245, 248, 255);
            private Color hover = new Color(220, 235, 255);
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
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (isSelected) {
                    c.setBackground(new Color(0,120,215));
                    c.setForeground(Color.WHITE);
                } else if (row == hoverRow) {
                    c.setBackground(hover);
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(row % 2 == 0 ? even : odd);
                    c.setForeground(Color.BLACK);
                }
                setHorizontalAlignment(CENTER);
                return c;
            }
        });

        JScrollPane sp = new JScrollPane(tbl);
        sp.setBorder(BorderFactory.createTitledBorder("Danh sách nhân viên"));
        add(sp, BorderLayout.CENTER);

        // ==== Form nhập liệu ====
        tfName   = new JTextField();
        tfPhone  = new JTextField();
        tfCCCD   = new JTextField();
        tfAddress= new JTextField();
        tfRole   = new JTextField();
        tfUser   = new JTextField();
        tfPass   = new JPasswordField();
        tfSalary = new JTextField();

        for (JComponent field : new JComponent[]{tfName, tfPhone, tfCCCD, tfAddress, tfRole, tfUser, tfPass, tfSalary}) {
            field.setBackground(Color.WHITE);
            field.setForeground(Color.BLACK);
            field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        }

        lblImage = new JLabel("Chưa chọn ảnh", SwingConstants.CENTER);
        lblImage.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        lblImage.setPreferredSize(new Dimension(120,120));

        btnChooseImg = new JButton("Chọn ảnh");

        JPanel imgPanel = new JPanel(new BorderLayout(5,5));
        imgPanel.add(lblImage, BorderLayout.CENTER);
        imgPanel.add(btnChooseImg, BorderLayout.SOUTH);
        imgPanel.setBorder(BorderFactory.createTitledBorder("Ảnh"));

        JPanel left = new JPanel(new GridLayout(8,2,8,8));
        left.setBorder(BorderFactory.createTitledBorder("Thông tin nhân viên"));
        left.add(new JLabel("Tên:")); left.add(tfName);
        left.add(new JLabel("SĐT:")); left.add(tfPhone);
        left.add(new JLabel("CCCD:")); left.add(tfCCCD);
        left.add(new JLabel("Quê quán:")); left.add(tfAddress);
        left.add(new JLabel("Vị trí:")); left.add(tfRole);
        left.add(new JLabel("Tài khoản:")); left.add(tfUser);
        left.add(new JLabel("Mật khẩu:")); left.add(tfPass);
        left.add(new JLabel("Lương:")); left.add(tfSalary);

        JPanel formContainer = new JPanel(new BorderLayout(10,10));
        formContainer.add(left, BorderLayout.CENTER);
        formContainer.add(imgPanel, BorderLayout.EAST);
        add(formContainer, BorderLayout.NORTH);

        // click vào khoảng trống form sẽ clear
        formContainer.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tbl.clearSelection();
                clearForm();
            }
        });

        // ==== Nút chức năng ====
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnAdd = macButton(" Thêm", new Color(40,167,69));
        btnEdit = macButton("️ Sửa", new Color(255,193,7));
        btnDelete = macButton(" Xóa", new Color(220,53,69));
        btnView = macButton(" Xem", new Color(0,123,255));

        buttons.add(btnAdd); buttons.add(btnEdit);
        buttons.add(btnDelete); buttons.add(btnView);
        add(buttons, BorderLayout.SOUTH);

        // ==== Chọn ảnh ====
        btnChooseImg.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                ImageIcon icon = new ImageIcon(new ImageIcon(file.getAbsolutePath())
                        .getImage().getScaledInstance(120,120,Image.SCALE_SMOOTH));
                lblImage.setIcon(icon);
                lblImage.setText("");
                lblImage.putClientProperty("path", file.getAbsolutePath());
            }
        });

        // ==== Sự kiện CRUD ====
        btnAdd.addActionListener(e -> insert());
        btnEdit.addActionListener(e -> update());
        btnDelete.addActionListener(e -> delete());
        btnView.addActionListener(e -> viewInfo());

        // ==== Click bảng để hiển thị lên form ====
        tbl.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int row = tbl.getSelectedRow();
                if (row >= 0) {
                    tfName.setText(model.getValueAt(row, 1).toString());
                    tfPhone.setText(model.getValueAt(row, 2).toString());
                    tfCCCD.setText(model.getValueAt(row, 3).toString());
                    tfAddress.setText(model.getValueAt(row, 4).toString());
                    Object roleObj = model.getValueAt(row, 5);
                    tfRole.setText(roleObj == null ? "" : roleObj.toString());
                    Object userObj = model.getValueAt(row, 6);
                    tfUser.setText(userObj == null ? "" : userObj.toString());
                    Object passObj = model.getValueAt(row, 7);
                    tfPass.setText(passObj == null ? "" : passObj.toString());
                    tfSalary.setText(model.getValueAt(row, 8).toString());
                    Object imgPath = model.getValueAt(row, 9);
                    if (imgPath != null) {
                        ImageIcon icon = new ImageIcon(new ImageIcon(imgPath.toString())
                                .getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH));
                        lblImage.setIcon(icon);
                        lblImage.setText("");
                        lblImage.putClientProperty("path", imgPath.toString());
                    } else {
                        lblImage.setIcon(null);
                        lblImage.setText("Chưa chọn ảnh");
                        lblImage.putClientProperty("path", null);
                    }
                }
            }
        });

        // ==== Click ra ngoài JTable để clear selection ====
        sp.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tbl.clearSelection();
                clearForm();
            }
        });

        load();
    }

    private JButton macButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBorder(BorderFactory.createEmptyBorder(8,15,8,15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { btn.setBackground(bg.darker()); }
            public void mouseExited(java.awt.event.MouseEvent evt) { btn.setBackground(bg); }
        });
        return btn;
    }

    void load() {
        model.setRowCount(0);
        try (Connection c = DBHelper.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM Employees")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("id"), rs.getString("name"), rs.getString("phone"),
                        rs.getString("cccd"), rs.getString("address"), rs.getString("position"),
                        rs.getString("username"), rs.getString("password"),
                        rs.getBigDecimal("salary"), rs.getString("imagePath")
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi load: " + ex.getMessage());
        }
    }

 // ===== INSERT (thêm ràng buộc username & phone) =====
private void insert() {
    String sqlEmp = "INSERT INTO Employees(name, phone, cccd, address, position, username, password, salary, imagePath, user_id) VALUES(?,?,?,?,?,?,?,?,?,?)";

    try (Connection c = DBHelper.getConnection()) {
        String username = tfUser.getText().trim();
        String phone = tfPhone.getText().trim();
        String password = new String(tfPass.getPassword()).trim();
        String role = tfRole.getText().trim().toUpperCase();

        if (phone.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Phải nhập số điện thoại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Kiểm tra trùng số điện thoại
        try (PreparedStatement psCheck = c.prepareStatement("SELECT COUNT(*) FROM Employees WHERE phone=?")) {
            psCheck.setString(1, phone);
            ResultSet rs = psCheck.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                JOptionPane.showMessageDialog(this, "Số điện thoại đã tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        int userId = -1;
        // Nếu vị trí là ADMIN, ORDER hoặc WAREHOUSE → tạo tài khoản
        if (role.equals("ADMIN") || role.equals("ORDER") || role.equals("WAREHOUSE")) {
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vị trí này bắt buộc phải có tài khoản (username + password)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Kiểm tra trùng username
            try (PreparedStatement psCheckUser = c.prepareStatement("SELECT COUNT(*) FROM Users WHERE username=?")) {
                psCheckUser.setString(1, username);
                ResultSet rs = psCheckUser.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    JOptionPane.showMessageDialog(this, "Tên đăng nhập đã tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            // Thêm vào Users
            try (PreparedStatement psUser = c.prepareStatement(
                    "INSERT INTO Users(username, password, role) VALUES(?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                psUser.setString(1, username);
                psUser.setString(2, password);
                psUser.setString(3, role);
                psUser.executeUpdate();

                ResultSet rsUser = psUser.getGeneratedKeys();
                if (rsUser.next()) userId = rsUser.getInt(1);
            }
        }

        // Thêm Employees
        try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
            ps.setString(1, tfName.getText().trim());
            ps.setString(2, phone);
            ps.setString(3, tfCCCD.getText().trim());
            ps.setString(4, tfAddress.getText().trim());
            ps.setString(5, role);
            ps.setString(6, (userId > 0) ? username : null);
            ps.setString(7, (userId > 0) ? password : null);

            String salaryText = tfSalary.getText().trim();
            ps.setBigDecimal(8, salaryText.isEmpty() ? java.math.BigDecimal.ZERO :
                    new java.math.BigDecimal(salaryText.replace(",", ".")));

            ps.setString(9, (String) lblImage.getClientProperty("path"));

            if (userId > 0)
                ps.setInt(10, userId);
            else
                ps.setNull(10, java.sql.Types.INTEGER);

            ps.executeUpdate();
        }

        JOptionPane.showMessageDialog(this, "✅ Thêm nhân viên thành công!");
        load();
        clearForm();
          ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();

    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "❌ Lỗi thêm: " + ex.getMessage());
        ex.printStackTrace();
    }
}

// ====== CẬP NHẬT NHÂN VIÊN + ĐỒNG BỘ USERS ======
private void update() {
    int row = tbl.getSelectedRow();
    if (row < 0) {
        JOptionPane.showMessageDialog(this, "Chọn nhân viên để sửa");
        return;
    }

    int id = (int) model.getValueAt(row, 0);

    try (Connection c = DBHelper.getConnection()) {
        // --- Lấy user_id hiện tại ---
        int userId = -1;
        try (PreparedStatement psGet = c.prepareStatement("SELECT user_id FROM Employees WHERE id=?")) {
            psGet.setInt(1, id);
            ResultSet rs = psGet.executeQuery();
            if (rs.next()) userId = rs.getInt("user_id");
        }

        String username = tfUser.getText().trim();
        String password = new String(tfPass.getPassword()).trim();
        String role = tfRole.getText().trim();

        // === Nếu thiếu 1 trong 3 trường thì xóa user và gỡ liên kết ===
        if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
            if (userId > 0) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "⚠️ Thiếu thông tin tài khoản. Bạn có muốn xóa tài khoản user này không?",
                        "Xác nhận xóa user",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    // 1️⃣ Gỡ liên kết trong Employees trước
                    try (PreparedStatement psUnlink = c.prepareStatement("UPDATE Employees SET user_id=NULL WHERE id=?")) {
                        psUnlink.setInt(1, id);
                        psUnlink.executeUpdate();
                    }

                    // 2️⃣ Xóa user trong bảng Users
                    try (PreparedStatement psDel = c.prepareStatement("DELETE FROM Users WHERE id=?")) {
                        psDel.setInt(1, userId);
                        psDel.executeUpdate();
                    }

                    JOptionPane.showMessageDialog(this, "🗑️ Đã xóa tài khoản user liên kết!");
                }
            }

            // Tiếp tục cập nhật thông tin nhân viên (nhưng không có user)
            userId = -1;
        } else {
            // === Nếu có user_id thì cập nhật, ngược lại tạo mới ===
            if (userId > 0) {
                try (PreparedStatement psUser = c.prepareStatement(
                        "UPDATE Users SET username=?, password=?, role=? WHERE id=?")) {
                    psUser.setString(1, username);
                    psUser.setString(2, password);
                    psUser.setString(3, role);
                    psUser.setInt(4, userId);
                    psUser.executeUpdate();
                }
            } else {
                try (PreparedStatement psUser = c.prepareStatement(
                        "INSERT INTO Users(username, password, role) VALUES(?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    psUser.setString(1, username);
                    psUser.setString(2, password);
                    psUser.setString(3, role);
                    psUser.executeUpdate();

                    ResultSet rs = psUser.getGeneratedKeys();
                    if (rs.next()) userId = rs.getInt(1);

                    try (PreparedStatement psLink = c.prepareStatement("UPDATE Employees SET user_id=? WHERE id=?")) {
                        psLink.setInt(1, userId);
                        psLink.setInt(2, id);
                        psLink.executeUpdate();
                    }
                }
            }
        }

        // === Cập nhật Employees ===
        String sqlEmp = "UPDATE Employees SET name=?, phone=?, cccd=?, address=?, position=?, username=?, password=?, salary=?, imagePath=? WHERE id=?";
        try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
            ps.setString(1, tfName.getText().trim());
            ps.setString(2, tfPhone.getText().trim());
            ps.setString(3, tfCCCD.getText().trim());
            ps.setString(4, tfAddress.getText().trim());
            ps.setString(5, role);
            ps.setString(6, username.isEmpty() ? null : username);
            ps.setString(7, password.isEmpty() ? null : password);

            String salaryText = tfSalary.getText().trim();
            ps.setBigDecimal(8, salaryText.isEmpty()
                    ? java.math.BigDecimal.ZERO
                    : new java.math.BigDecimal(salaryText.replace(",", ".")));
            ps.setString(9, (String) lblImage.getClientProperty("path"));
            ps.setInt(10, id);
            ps.executeUpdate();
        }

        JOptionPane.showMessageDialog(this, "✅ Cập nhật thành công!");
        load();
        clearForm();
        ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();

    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "❌ Lỗi khi sửa: " + ex.getMessage());
        ex.printStackTrace();
    }
}


// ====== XÓA NHÂN VIÊN + USERS ======
private void delete() {
    int row = tbl.getSelectedRow();
    if (row < 0) {
        JOptionPane.showMessageDialog(this, "Chọn nhân viên để xóa!");
        return;
    }

    int id = (int) model.getValueAt(row, 0);
    int confirm = JOptionPane.showConfirmDialog(this,
            "Bạn có chắc chắn muốn xóa nhân viên ID = " + id + " ?",
            "Xác nhận xóa",
            JOptionPane.YES_NO_OPTION);
    if (confirm != JOptionPane.YES_OPTION) return;

    try (Connection c = DBHelper.getConnection()) {
        // Lấy user_id
        int userId = -1;
        try (PreparedStatement psGet = c.prepareStatement("SELECT user_id FROM Employees WHERE id=?")) {
            psGet.setInt(1, id);
            ResultSet rs = psGet.executeQuery();
            if (rs.next()) userId = rs.getInt("user_id");
        }

        // Xóa Employees
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM Employees WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

        // Xóa Users nếu có
        if (userId > 0) {
            try (PreparedStatement psAcc = c.prepareStatement("DELETE FROM Users WHERE id=?")) {
                psAcc.setInt(1, userId);
                psAcc.executeUpdate();
            }
        }

        JOptionPane.showMessageDialog(this, "🗑️ Xóa thành công!");
        load();
        clearForm();
        ((MainFrame) SwingUtilities.getWindowAncestor(this)).refreshAll();

    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "❌ Lỗi khi xóa: " + ex.getMessage());
        ex.printStackTrace();
    }
}


    private void clearForm() {
        tfName.setText(""); tfPhone.setText(""); tfCCCD.setText("");
        tfAddress.setText(""); tfRole.setText(""); tfUser.setText("");
        tfPass.setText(""); tfSalary.setText("");
        lblImage.setIcon(null); lblImage.setText("Chưa chọn ảnh");
        lblImage.putClientProperty("path", null);
    }

    // ===== View đẹp hơn =====
    private void viewInfo() {
        int row = tbl.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Chọn nhân viên để xem"); return; }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Thông tin nhân viên", true);
        dialog.setSize(550, 350);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10,10));
        dialog.getContentPane().setBackground(Color.WHITE);

        JPanel infoPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));
        infoPanel.setBackground(Color.WHITE);
        String[] labels = {"ID","Tên","SĐT","CCCD","Quê quán","Vị trí","Tài khoản","Lương"};
        for (int i = 0; i < labels.length; i++) {
            JLabel lbl = new JLabel(labels[i]+":", SwingConstants.RIGHT);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            JLabel val = new JLabel(String.valueOf(model.getValueAt(row, i)), SwingConstants.LEFT);
            val.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            infoPanel.add(lbl); infoPanel.add(val);
        }

        Object imgPath = model.getValueAt(row, 9);
        JLabel imgLabel = new JLabel();
        imgLabel.setPreferredSize(new Dimension(180,180));
        imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
        if (imgPath != null) {
            imgLabel.setIcon(new ImageIcon(new ImageIcon(imgPath.toString())
                    .getImage().getScaledInstance(180,180,Image.SCALE_SMOOTH)));
        } else imgLabel.setText("Không có ảnh");

        JPanel left = new JPanel(new BorderLayout());
        left.add(imgLabel, BorderLayout.CENTER);
        left.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));
        left.setBackground(Color.WHITE);

        JButton close = new JButton("Đóng");
        close.addActionListener(e -> dialog.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(Color.WHITE);
        bottom.add(close);

        dialog.add(left, BorderLayout.WEST);
        dialog.add(infoPanel, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
}
