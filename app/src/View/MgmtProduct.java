/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package View;

import Controller.BundleBService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author beepxD
 */
public class MgmtProduct extends javax.swing.JPanel {

    private final BundleBService service;
    public DefaultTableModel tableModel;
    private List<BundleBService.CatalogProduct> products = Collections.emptyList();
    
    public MgmtProduct(BundleBService service) {
        initComponents();
        this.service = service;
        tableModel = (DefaultTableModel)table.getModel();
        table.getTableHeader().setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14));
    }

    public void init(){
        boolean manage = service.canManageProducts();
        boolean purchase = service.canPurchase();
        purchaseBtn.setVisible(purchase);
        addBtn.setVisible(manage);
        editBtn.setVisible(manage);
        deleteBtn.setVisible(manage);
        products = Collections.emptyList();
        tableModel.setRowCount(0);
        BundleBService.Result<List<BundleBService.CatalogProduct>> result = service.listCatalog();
        if (!result.isSuccess()) {
            if (result.getStatus() != BundleBService.Status.DENIED) { showResult(result); }
            return;
        }
        products = new ArrayList<BundleBService.CatalogProduct>(result.getValue());
        for (BundleBService.CatalogProduct product : products) {
            tableModel.addRow(new Object[] { product.getName(), product.getStock(), product.getPriceDisplay() });
        }
    }

    private BundleBService.CatalogProduct selectedProduct() {
        int row = table.getSelectedRow();
        if (row < 0) { return null; }
        int modelRow = table.convertRowIndexToModel(row);
        return modelRow >= 0 && modelRow < products.size() ? products.get(modelRow) : null;
    }

    private void showResult(BundleBService.Result<?> result) {
        JOptionPane.showMessageDialog(this, result.getSafeMessage(), "Products", JOptionPane.INFORMATION_MESSAGE);
    }

    private ProductInput askProduct(String title, BundleBService.CatalogProduct existing) {
        JTextField name = new JTextField(existing == null ? "" : existing.getName());
        JTextField stock = new JTextField(existing == null ? "" : Integer.toString(existing.getStock()));
        JTextField price = new JTextField(existing == null || existing.getPriceMinor() == null ? "" : existing.getPriceDisplay());
        JPasswordField password = new JPasswordField();
        designer(name, "PRODUCT NAME");
        designer(stock, "PRODUCT STOCK");
        designer(price, "PRODUCT PRICE");
        designer(password, "CURRENT PASSWORD");
        Object[] fields = { "Product details (letters and digits only):", name, stock, price, password };
        while (true) {
            if (JOptionPane.showConfirmDialog(this, fields, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                password.setText("");
                return null;
            }
            char[] secret = password.getPassword();
            password.setText("");
            String productName = name.getText();
            String productStock = stock.getText();
            String productPrice = price.getText();
            if (BundleBService.isValidProductInput(productName, productStock, productPrice)) {
                return new ProductInput(productName, productStock, productPrice, secret);
            }
            if (existing == null) {
                BundleBService.Result<BundleBService.CatalogProduct> result =
                        service.addProduct(productName, productStock, productPrice, secret);
                showResult(result);
                if (result.getStatus() != BundleBService.Status.INVALID) { return null; }
            } else {
                BundleBService.Result<BundleBService.CatalogProduct> result =
                        service.editProduct(existing, productName, productStock, productPrice, secret);
                showResult(result);
                if (result.getStatus() != BundleBService.Status.INVALID) { return null; }
            }
        }
    }

    private JPasswordField askCurrentPassword() {
        JPasswordField password = new JPasswordField();
        designer(password, "CURRENT PASSWORD");
        if (JOptionPane.showConfirmDialog(this, password, "VERIFY CURRENT PASSWORD", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            password.setText("");
            return null;
        }
        return password;
    }

    private static final class ProductInput {
        final String name; final String stock; final String price; final char[] password;
        ProductInput(String name, String stock, String price, char[] password) {
            this.name = name; this.stock = stock; this.price = price; this.password = password;
        }
    }
    
    public void designer(JTextField component, String text){
        component.setSize(70, 600);
        component.setFont(new java.awt.Font("Tahoma", 0, 18));
        component.setBackground(new java.awt.Color(240, 240, 240));
        component.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        component.setBorder(javax.swing.BorderFactory.createTitledBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 2, true), text, javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12)));
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        table = new javax.swing.JTable();
        purchaseBtn = new javax.swing.JButton();
        addBtn = new javax.swing.JButton();
        editBtn = new javax.swing.JButton();
        deleteBtn = new javax.swing.JButton();

        table.setFont(new java.awt.Font("SansSerif", 0, 14)); // NOI18N
        table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null},
                {null, null, null},
                {null, null, null},
                {null, null, null}
            },
            new String [] {
                "Name", "Stock", "Price"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(table);
        if (table.getColumnModel().getColumnCount() > 0) {
            table.getColumnModel().getColumn(0).setMinWidth(50);
            table.getColumnModel().getColumn(1).setMaxWidth(100);
            table.getColumnModel().getColumn(2).setMaxWidth(100);
        }

        purchaseBtn.setBackground(new java.awt.Color(255, 255, 255));
        purchaseBtn.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        purchaseBtn.setText("PURCHASE");
        purchaseBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                purchaseBtnActionPerformed(evt);
            }
        });

        addBtn.setBackground(new java.awt.Color(255, 255, 255));
        addBtn.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        addBtn.setText("ADD");
        addBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addBtnActionPerformed(evt);
            }
        });

        editBtn.setBackground(new java.awt.Color(255, 255, 255));
        editBtn.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        editBtn.setText("EDIT");
        editBtn.setToolTipText("");
        editBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editBtnActionPerformed(evt);
            }
        });

        deleteBtn.setBackground(new java.awt.Color(255, 255, 255));
        deleteBtn.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        deleteBtn.setText("DELETE");
        deleteBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteBtnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(purchaseBtn, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(0, 0, 0)
                        .addComponent(addBtn, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(0, 0, 0)
                        .addComponent(editBtn, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(0, 0, 0)
                        .addComponent(deleteBtn, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jScrollPane1))
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 222, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(deleteBtn, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(addBtn, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(purchaseBtn, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(editBtn, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void purchaseBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_purchaseBtnActionPerformed
        BundleBService.CatalogProduct product = selectedProduct();
        if (product == null) { showNoSelection(); return; }
        JTextField quantity = new JTextField("1");
        designer(quantity, "QUANTITY");
        Object[] message = { "How many " + product.getName() + " do you want?", quantity };
        if (JOptionPane.showConfirmDialog(this, message, "PURCHASE PRODUCT", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) { return; }
        BundleBService.Result<BundleBService.PurchaseQuote> quoted = service.quotePurchase(product.getId(), quantity.getText());
        if (!quoted.isSuccess()) { showResult(quoted); return; }
        BundleBService.PurchaseQuote quote = quoted.getValue();
        String confirmation = "Product: " + quote.getProductName() + "\nQuantity: " + quote.getQuantity()
                + "\nUnit price: " + quote.getUnitPriceDisplay() + "\nTotal: " + quote.getTotalDisplay();
        if (JOptionPane.showConfirmDialog(this, confirmation, "CONFIRM PURCHASE", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            service.cancelPurchaseQuote(quote);
            return;
        }
        BundleBService.Result<BundleBService.PurchaseRecord> result = service.confirmPurchase(quote);
        showResult(result);
        if (result.isSuccess() || result.getStatus() == BundleBService.Status.CONFLICT
                || result.getStatus() == BundleBService.Status.NOT_FOUND) { init(); }
    }//GEN-LAST:event_purchaseBtnActionPerformed

    private void addBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addBtnActionPerformed
        ProductInput input = askProduct("ADD PRODUCT", null);
        if (input == null) { return; }
        BundleBService.Result<BundleBService.CatalogProduct> result = service.addProduct(input.name, input.stock, input.price, input.password);
        showResult(result);
        if (result.isSuccess()) { init(); }
    }//GEN-LAST:event_addBtnActionPerformed

    private void editBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editBtnActionPerformed
        BundleBService.CatalogProduct selected = selectedProduct();
        if (selected == null) { showNoSelection(); return; }
        ProductInput input = askProduct("EDIT PRODUCT", selected);
        if (input == null) { return; }
        BundleBService.Result<BundleBService.CatalogProduct> result =
                service.editProduct(selected, input.name, input.stock, input.price, input.password);
        showResult(result);
        if (result.isSuccess() || result.getStatus() == BundleBService.Status.CONFLICT) { init(); }
    }//GEN-LAST:event_editBtnActionPerformed

    private void deleteBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteBtnActionPerformed
        BundleBService.CatalogProduct selected = selectedProduct();
        if (selected == null) { showNoSelection(); return; }
        if (JOptionPane.showConfirmDialog(this, "Delete " + selected.getName() + "?", "DELETE PRODUCT",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) { return; }
        JPasswordField password = askCurrentPassword();
        if (password == null) { return; }
        char[] secret = password.getPassword();
        password.setText("");
        BundleBService.Result<Void> result = service.deleteProduct(selected, secret);
        Arrays.fill(secret, '\0');
        showResult(result);
        if (result.isSuccess() || result.getStatus() == BundleBService.Status.CONFLICT) { init(); }
    }//GEN-LAST:event_deleteBtnActionPerformed

    private void showNoSelection() {
        JOptionPane.showMessageDialog(this, "Select a product first.", "Products", JOptionPane.INFORMATION_MESSAGE);
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addBtn;
    private javax.swing.JButton deleteBtn;
    private javax.swing.JButton editBtn;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton purchaseBtn;
    private javax.swing.JTable table;
    // End of variables declaration//GEN-END:variables
}
