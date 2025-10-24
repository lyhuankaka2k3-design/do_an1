//import javax.swing.*;
//import javax.swing.table.DefaultTableCellRenderer;
//import javax.swing.table.DefaultTableModel;
//import java.awt.*;
//import java.io.File;
//import java.sql.*;
//
//public class ProductsDetailPanel extends JPanel {
//    private DefaultTableModel model;
//    private JTable tbl;
//    private JLabel lblImage;
//    private JButton btnAdd, btnEdit, btnDelete, btnChooseImg, btnSearch, btnView;
//    private JTextField tfName, tfSupplier, tfPhone, tfExpire, tfImportPrice, tfSearch;
//    private JComboBox<String> cbStatus;
//
//    public ProductsDetailPanel() {
//        setLayout(new BorderLayout(10, 10));
//        setBackground(Color.WHITE);
//
//        // ==== Bảng sản phẩm ====
//        model = new DefaultTableModel(new String[]{
//                "mã sản phẩm mẫu ","Tên SP","Nhà CC","SĐT NCC","Hạn sử dụng","Giá nhập","Ảnh","Tình trạng"
//        }, 0);
//        tbl = new JTable(model);
//        tbl.setRowHeight(28);
//        tbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
//        tbl.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
//        tbl.getTableHeader().setBackground(new Color(0,120,215));
//        tbl.getTableHeader().setForeground(Color.WHITE);
//
//        // Zebra stripe
//        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
//            private Color even = Color.WHITE;
//            private Color odd = new Color(245, 248, 255);
//            @Override
//            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
//                                                           boolean hasFocus, int row, int col) {
//                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
//                if (isSelected) {
//                    c.setBackground(new Color(0,120,215));
//                    c.setForeground(Color.WHITE);
//                } else {
//                    c.setBackground(row % 2 == 0 ? even : odd);
//                    c.setForeground(Color.BLACK);
//                }
//                setHorizontalAlignment(CENTER);
//                return c;
//            }
//        });
//
//        JScrollPane sp = new JScrollPane(tbl);
//        sp.setBorder(BorderFactory.createTitledBorder("Danh sách sản phẩm"));
//        add(sp, BorderLayout.CENTER);
//
//        // ==== Form nhập liệu ====
//        tfName = new JTextField();
//        tfSupplier = new JTextField();
//        tfPhone = new JTextField();
//        tfExpire = new JTextField("yyyy-MM-dd"); // format ngày
//        tfImportPrice = new JTextField();
//        cbStatus = new JComboBox<>(new String[]{"Đang bán","Đợi bán"});
//
//        lblImage = new JLabel("Chưa chọn ảnh", SwingConstants.CENTER);
//        lblImage.setBorder(BorderFactory.createLineBorder(Color.GRAY));
//        lblImage.setPreferredSize(new Dimension(120,120));
//
//        btnChooseImg = new JButton("Chọn ảnh");
//
//        JPanel imgPanel = new JPanel(new BorderLayout(5,5));
//        imgPanel.add(lblImage, BorderLayout.CENTER);
//        imgPanel.add(btnChooseImg, BorderLayout.SOUTH);
//        imgPanel.setBorder(BorderFactory.createTitledBorder("Ảnh SP"));
//
//        JPanel left = new JPanel(new GridLayout(6,2,8,8));
//        left.setBorder(BorderFactory.createTitledBorder("Thông tin sản phẩm"));
//        left.add(new JLabel("Tên SP:")); left.add(tfName);
//        left.add(new JLabel("Nhà CC:")); left.add(tfSupplier);
//        left.add(new JLabel("SĐT NCC:")); left.add(tfPhone);
//        left.add(new JLabel("Hạn SD:")); left.add(tfExpire);
//        left.add(new JLabel("Giá nhập:")); left.add(tfImportPrice);
//        left.add(new JLabel("Tình trạng:")); left.add(cbStatus);
//
//        JPanel formContainer = new JPanel(new BorderLayout(10,10));
//        formContainer.add(left, BorderLayout.CENTER);
//        formContainer.add(imgPanel, BorderLayout.EAST);
//        add(formContainer, BorderLayout.NORTH);
//
//        // ==== Thanh tìm kiếm + nút ====
//        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
//        tfSearch = new JTextField(20);
//        btnSearch = new JButton("Tìm");
//        topBar.add(new JLabel("Tìm theo tên:"));
//        topBar.add(tfSearch);
//        topBar.add(btnSearch);
//        formContainer.add(topBar, BorderLayout.SOUTH);
//
//        // ==== Nút chức năng ====
//        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
//        btnAdd = macButton("Thêm", new Color(40,167,69));
//        btnEdit = macButton("Sửa", new Color(255,193,7));
//        btnDelete = macButton("Xóa", new Color(220,53,69));
//        btnView = macButton("Xem", new Color(0,123,255));
//        buttons.add(btnAdd); buttons.add(btnEdit); buttons.add(btnDelete); buttons.add(btnView);
//        add(buttons, BorderLayout.SOUTH);
//
//        // ==== Chọn ảnh ====
//        btnChooseImg.addActionListener(e -> {
//            JFileChooser chooser = new JFileChooser();
//            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
//                File file = chooser.getSelectedFile();
//                ImageIcon icon = new ImageIcon(new ImageIcon(file.getAbsolutePath())
//                        .getImage().getScaledInstance(120,120,Image.SCALE_SMOOTH));
//                lblImage.setIcon(icon);
//                lblImage.setText("");
//                lblImage.putClientProperty("path", file.getAbsolutePath());
//            }
//        });
//
//        // ==== Sự kiện CRUD ====
//        btnAdd.addActionListener(e -> insert());
//        btnEdit.addActionListener(e -> update());
//        btnDelete.addActionListener(e -> delete());
//        btnSearch.addActionListener(e -> search(tfSearch.getText().trim()));
//        btnView.addActionListener(e -> viewProduct());
//
//        // ==== Click bảng ====
//        tbl.addMouseListener(new java.awt.event.MouseAdapter() {
//            public void mouseClicked(java.awt.event.MouseEvent evt) {
//                int row = tbl.getSelectedRow();
//                if (row >= 0) {
//                    tfName.setText(model.getValueAt(row,1).toString());
//                    tfSupplier.setText(model.getValueAt(row,2).toString());
//                    tfPhone.setText(model.getValueAt(row,3).toString());
//                    tfExpire.setText(model.getValueAt(row,4).toString());
//                    tfImportPrice.setText(model.getValueAt(row,5).toString());
//                    cbStatus.setSelectedItem(model.getValueAt(row,7).toString());
//
//                    Object imgPath = model.getValueAt(row, 6);
//                    if (imgPath != null) {
//                        ImageIcon icon = new ImageIcon(new ImageIcon(imgPath.toString())
//                                .getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH));
//                        lblImage.setIcon(icon);
//                        lblImage.setText("");
//                        lblImage.putClientProperty("path", imgPath.toString());
//                    } else {
//                        lblImage.setIcon(null);
//                        lblImage.setText("Chưa chọn ảnh");
//                        lblImage.putClientProperty("path", null);
//                    }
//                }
//            }
//        });
//
//        // ==== Click ra ngoài bảng để clear form ====
//        sp.getViewport().addMouseListener(new java.awt.event.MouseAdapter() {
//            public void mouseClicked(java.awt.event.MouseEvent evt) {
//                int row = tbl.rowAtPoint(evt.getPoint());
//                if (row == -1) { // không click vào dòng nào
//                    tbl.clearSelection();
//                    clearForm();
//                }
//            }
//        });
//
//        // Load ban đầu
//        load();
//    }
//
//    // ==== Xem sản phẩm ====
//    private void viewProduct() {
//        int row = tbl.getSelectedRow();
//        if (row < 0) {
//            JOptionPane.showMessageDialog(this, "Chọn sản phẩm để xem");
//            return;
//        }
//
//        String name = model.getValueAt(row, 1).toString();
//        String supplier = model.getValueAt(row, 2).toString();
//        String phone = model.getValueAt(row, 3).toString();
//        String expire = model.getValueAt(row, 4).toString();
//        String importPrice = model.getValueAt(row, 5).toString();
//        String imagePath = model.getValueAt(row, 6) != null ? model.getValueAt(row, 6).toString() : null;
//        String status = model.getValueAt(row, 7).toString();
//
//        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Chi tiết sản phẩm", true);
//        dialog.setSize(550, 400);
//        dialog.setLocationRelativeTo(this);
//        dialog.setLayout(new BorderLayout(10, 10));
//
//        // Header đẹp
//        JLabel header = new JLabel("Thông tin chi tiết sản phẩm", SwingConstants.CENTER);
//        header.setFont(new Font("Segoe UI", Font.BOLD, 18));
//        header.setOpaque(true);
//        header.setBackground(new Color(0,123,255));
//        header.setForeground(Color.WHITE);
//        header.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
//
//        // Ảnh
//        JLabel imgLabel = new JLabel("Không có ảnh", SwingConstants.CENTER);
//        imgLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
//        if (imagePath != null && !imagePath.isEmpty()) {
//            ImageIcon icon = new ImageIcon(new ImageIcon(imagePath).getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH));
//            imgLabel.setIcon(icon);
//            imgLabel.setText("");
//        }
//
//        // Thông tin
//        JPanel infoPanel = new JPanel(new GridLayout(0,1,8,8));
//        infoPanel.add(new JLabel("Tên SP: " + name));
//        infoPanel.add(new JLabel("Nhà cung cấp: " + supplier));
//        infoPanel.add(new JLabel("SĐT NCC: " + phone));
//        infoPanel.add(new JLabel("Hạn sử dụng: " + expire));
//        infoPanel.add(new JLabel("Giá nhập: " + importPrice));
//        infoPanel.add(new JLabel("Tình trạng: " + status));
//
//        JPanel content = new JPanel(new BorderLayout(10,10));
//        content.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
//        content.add(imgLabel, BorderLayout.WEST);
//        content.add(infoPanel, BorderLayout.CENTER);
//
//        // Nút đóng
//        JButton btnClose = new JButton("Đóng");
//        btnClose.addActionListener(e -> dialog.dispose());
//        JPanel bottom = new JPanel();
//        bottom.add(btnClose);
//
//        dialog.add(header, BorderLayout.NORTH);
//        dialog.add(content, BorderLayout.CENTER);
//        dialog.add(bottom, BorderLayout.SOUTH);
//        dialog.setVisible(true);
//    }
//
//    // ==== Clear form ====
//    private void clearForm() {
//        tfName.setText("");
//        tfSupplier.setText("");
//        tfPhone.setText("");
//        tfExpire.setText("yyyy-MM-dd");
//        tfImportPrice.setText("");
//        cbStatus.setSelectedIndex(0);
//        lblImage.setIcon(null);
//        lblImage.setText("Chưa chọn ảnh");
//        lblImage.putClientProperty("path", null);
//    }
//
//    // ==== Button đẹp ====
//    private JButton macButton(String text, Color bg) {
//        JButton btn = new JButton(text);
//        btn.setFocusPainted(false);
//        btn.setBackground(bg);
//        btn.setForeground(Color.WHITE);
//        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
//        btn.setBorder(BorderFactory.createEmptyBorder(8,15,8,15));
//        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
//        btn.setOpaque(true);
//        btn.addMouseListener(new java.awt.event.MouseAdapter() {
//            public void mouseEntered(java.awt.event.MouseEvent evt) { btn.setBackground(bg.darker()); }
//            public void mouseExited(java.awt.event.MouseEvent evt) { btn.setBackground(bg); }
//        });
//        return btn;
//    }
//
//    // ==== LOAD ====
//    private void load() {
//        model.setRowCount(0);
//        try (Connection c = DBHelper.getConnection();
//             Statement st = c.createStatement();
//             ResultSet rs = st.executeQuery("SELECT * FROM ProductsDetail")) {
//            while (rs.next()) {
//                model.addRow(new Object[]{
//                        rs.getInt("id"), rs.getString("name"), rs.getString("supplier"),
//                        rs.getString("phone"), rs.getDate("expire"),
//                        rs.getBigDecimal("importPrice"),
//                        rs.getString("imagePath"), rs.getString("status")
//                });
//            }
//        } catch (Exception ex) {
//            JOptionPane.showMessageDialog(this, "Lỗi load: " + ex.getMessage());
//        }
//    }
//
//    // ==== INSERT ====
//    private void insert() {
//        String sql = "INSERT INTO ProductsDetail(name, supplier, phone, expire, importPrice, imagePath, status) VALUES(?,?,?,?,?,?,?)";
//        try (Connection c = DBHelper.getConnection();
//             PreparedStatement ps = c.prepareStatement(sql)) {
//            ps.setString(1, tfName.getText().trim());
//            ps.setString(2, tfSupplier.getText().trim());
//            ps.setString(3, tfPhone.getText().trim());
//            ps.setDate(4, java.sql.Date.valueOf(tfExpire.getText().trim()));
//            ps.setBigDecimal(5, new java.math.BigDecimal(tfImportPrice.getText().trim()));
//            ps.setString(6, (String) lblImage.getClientProperty("path"));
//            ps.setString(7, cbStatus.getSelectedItem().toString());
//            ps.executeUpdate();
//            JOptionPane.showMessageDialog(this, "Thêm thành công!");
//            load();
//            clearForm();
//        } catch (Exception ex) {
//            JOptionPane.showMessageDialog(this, "Lỗi thêm: " + ex.getMessage());
//        }
//    }
//
//    // ==== UPDATE ====
//    private void update() {
//        int row = tbl.getSelectedRow();
//        if (row < 0) { JOptionPane.showMessageDialog(this,"Chọn sản phẩm để sửa"); return; }
//        int id = (int) model.getValueAt(row, 0);
//
//        String sql = "UPDATE ProductsDetail SET name=?, supplier=?, phone=?, expire=?, importPrice=?, imagePath=?, status=? WHERE id=?";
//        try (Connection c = DBHelper.getConnection();
//             PreparedStatement ps = c.prepareStatement(sql)) {
//            ps.setString(1, tfName.getText().trim());
//            ps.setString(2, tfSupplier.getText().trim());
//            ps.setString(3, tfPhone.getText().trim());
//            ps.setDate(4, java.sql.Date.valueOf(tfExpire.getText().trim()));
//            ps.setBigDecimal(5, new java.math.BigDecimal(tfImportPrice.getText().trim()));
//            ps.setString(6, (String) lblImage.getClientProperty("path"));
//            ps.setString(7, cbStatus.getSelectedItem().toString());
//            ps.setInt(8, id);
//            ps.executeUpdate();
//            JOptionPane.showMessageDialog(this, "Sửa thành công!");
//            load();
//            clearForm();
//        } catch (Exception ex) {
//            JOptionPane.showMessageDialog(this, "Lỗi sửa: " + ex.getMessage());
//        }
//    }
//
//    // ==== DELETE ====
//    private void delete() {
//        int row = tbl.getSelectedRow();
//        if (row < 0) { JOptionPane.showMessageDialog(this,"Chọn sản phẩm để xóa"); return; }
//        int id = (int) model.getValueAt(row, 0);
//        if (JOptionPane.showConfirmDialog(this, "Xóa sản phẩm ID=" + id + " ?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
//            try (Connection c = DBHelper.getConnection();
//                 PreparedStatement ps = c.prepareStatement("DELETE FROM ProductsDetail WHERE id=?")) {
//                ps.setInt(1, id);
//                ps.executeUpdate();
//                JOptionPane.showMessageDialog(this, "Xóa thành công!");
//                load();
//                clearForm();
//            } catch (Exception ex) {
//                JOptionPane.showMessageDialog(this, "Lỗi xóa: " + ex.getMessage());
//            }
//        }
//    }
//
//    // ==== SEARCH ====
//    private void search(String keyword) {
//        model.setRowCount(0);
//        String sql = "SELECT * FROM ProductsDetail WHERE name LIKE ?";
//        try (Connection c = DBHelper.getConnection();
//             PreparedStatement ps = c.prepareStatement(sql)) {
//            ps.setString(1, "%" + keyword + "%");
//            ResultSet rs = ps.executeQuery();
//            while (rs.next()) {
//                model.addRow(new Object[]{
//                        rs.getInt("id"), rs.getString("name"), rs.getString("supplier"),
//                        rs.getString("phone"), rs.getDate("expire"),
//                        rs.getBigDecimal("importPrice"),
//                        rs.getString("imagePath"), rs.getString("status")
//                });
//            }
//        } catch (Exception ex) {
//            JOptionPane.showMessageDialog(this, "Lỗi tìm kiếm: " + ex.getMessage());
//        }
//    }
//}
