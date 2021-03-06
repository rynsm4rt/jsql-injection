/*******************************************************************************
 * Copyhacked (H) 2012-2016.
 * This program and the accompanying materials
 * are made available under no term at all, use it like
 * you want, but share and discuss about it
 * every time possible with every body.
 *
 * Contributors:
 *      ron190 at ymail dot com - initial implementation
 *******************************************************************************/
package com.jsql.view.swing.panel;

import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.DefaultCaret;

import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import com.jsql.i18n.I18n;
import com.jsql.model.InjectionModel;
import com.jsql.model.bean.util.HttpHeader;
import com.jsql.util.StringUtil;
import com.jsql.view.swing.HelperUi;
import com.jsql.view.swing.MediatorGui;
import com.jsql.view.swing.console.JavaConsoleAdapter;
import com.jsql.view.swing.console.SimpleConsoleAdapter;
import com.jsql.view.swing.console.SwingAppender;
import com.jsql.view.swing.panel.util.HTMLEditorKitTextPaneWrap;
import com.jsql.view.swing.popupmenu.JPopupMenuTable;
import com.jsql.view.swing.scrollpane.JScrollIndicator;
import com.jsql.view.swing.scrollpane.LightScrollPane;
import com.jsql.view.swing.splitpane.JSplitPaneWithZeroSizeDivider;
import com.jsql.view.swing.tab.MouseTabbedPane;
import com.jsql.view.swing.tab.TabConsoles;
import com.jsql.view.swing.text.JPopupTextArea;
import com.jsql.view.swing.text.JTextAreaPlaceholderConsole;
import com.jsql.view.swing.text.JTextPanePlaceholder;
import com.jsql.view.swing.ui.CustomMetalTabbedPaneUI;

/**
 * A panel with different consoles displayed on the bottom.
 */
@SuppressWarnings("serial")
public class PanelConsoles extends JPanel {
    
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = Logger.getRootLogger();
	
    /**
     * Console for default application messages.
     */
    private SimpleConsoleAdapter consoleTextPane = new SimpleConsoleAdapter("Console", "Event logging");

    /**
     * Console for java exception messages.
     */
    private JavaConsoleAdapter javaTextPane = new JavaConsoleAdapter("Java", "Java unhandled exception");
    
    /**
     * Console for raw SQL results.
     */
    private JTextArea chunkTextArea;

    /**
     * Panel displaying table of HTTP requests and responses.
     */
    private JSplitPaneWithZeroSizeDivider network;

    /**
     * Console for binary representation of characters found with blind/time injection.
     */
    private JTextArea binaryTextArea;

    /**
     * List of HTTP injection requests and responses.
     */
    private transient List<HttpHeader> listHttpHeader = new ArrayList<>();

    /**
     * Table in Network tab displaying HTTP requests.
     */
    private JTable networkTable;

    private JTextArea networkTabUrl = new JPopupTextArea("Request URL").getProxy();
    private JTextArea networkTabResponse = new JPopupTextArea("Header server response").getProxy();
    private JTextArea networkTabSource = new JPopupTextArea("Raw page source").getProxy();
    private JTextPane networkTabPreview = new JTextPanePlaceholder("Web browser rendering");
    private JTextArea networkTabHeader = new JPopupTextArea("Header client request").getProxy();
    private JTextArea networkTabParam = new JPopupTextArea("HTTP POST parameters").getProxy();
    
    /**
     * Create panel at the bottom with differents consoles to report injection process.
     */
    public PanelConsoles() {
        
        // Object creation after customization
        this.consoleTextPane.getProxy().setEditable(false);
        SwingAppender.register(this.consoleTextPane);
        
        this.chunkTextArea = new JPopupTextArea(new JTextAreaPlaceholderConsole("Raw data extracted during injection")).getProxy();
        this.chunkTextArea.setEditable(false);
        
        this.binaryTextArea = new JPopupTextArea(new JTextAreaPlaceholderConsole("Characters extracted during blind or time injection")).getProxy();
        this.binaryTextArea.setEditable(false);
        
        this.javaTextPane.getProxy().setEditable(false);
        SwingAppender.register(this.javaTextPane);
        
        this.network = new JSplitPaneWithZeroSizeDivider(JSplitPane.HORIZONTAL_SPLIT);
        this.network.setResizeWeight(1);
        this.network.setDividerSize(0);
        this.network.setDividerLocation(600);
        this.network.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, HelperUi.COLOR_COMPONENT_BORDER));
        this.networkTable = new JTable(0, 4) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        this.networkTable.setComponentPopupMenu(new JPopupMenuTable(this.networkTable));
        this.networkTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        this.networkTable.setRowSelectionAllowed(true);
        this.networkTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.networkTable.setRowHeight(20);
        this.networkTable.setGridColor(Color.LIGHT_GRAY);
        this.networkTable.getTableHeader().setReorderingAllowed(false);
        
        this.networkTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                PanelConsoles.this.networkTable.requestFocusInWindow();
                // move selected row and place cursor on focused cell
                if (SwingUtilities.isRightMouseButton(e)) {
                    Point p = e.getPoint();

                    // get the row index that contains that coordinate
                    int rowNumber = PanelConsoles.this.networkTable.rowAtPoint(p);
                    int colNumber = PanelConsoles.this.networkTable.columnAtPoint(p);
                    // Get the ListSelectionModel of the JTable
                    DefaultListSelectionModel  model = (DefaultListSelectionModel) PanelConsoles.this.networkTable.getSelectionModel();
                    DefaultListSelectionModel  model2 = (DefaultListSelectionModel) PanelConsoles.this.networkTable.getColumnModel().getSelectionModel();

                    PanelConsoles.this.networkTable.setRowSelectionInterval(rowNumber, rowNumber);
                    model.moveLeadSelectionIndex(rowNumber);
                    model2.moveLeadSelectionIndex(colNumber);
                }
            }
        });

        this.networkTable.setModel(new DefaultTableModel() {
            private String[] columns = {
                I18n.valueByKey("NETWORK_TAB_METHOD_COLUMN"),
                I18n.valueByKey("NETWORK_TAB_URL_COLUMN"),
                I18n.valueByKey("NETWORK_TAB_SIZE_COLUMN"),
                I18n.valueByKey("NETWORK_TAB_TYPE_COLUMN")
            };

            @Override
            public int getColumnCount() {
                return this.columns.length;
            }

            @Override
            public String getColumnName(int index) {
                return this.columns[index];
            }
        });

        class PathCellRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column
            ) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setToolTipText(
                    "<html><div style=\"font-size:10px;font-family:'Ubuntu Mono'\">"
                    + c.getText().replaceAll("(.{100})(?!$)", "$1<br>")
                    + "</div></html>"
                );
                
                return c;
            }
        }
        
        this.networkTable.getColumnModel().getColumn(1).setCellRenderer(new PathCellRenderer());
        
        class CenterRenderer extends DefaultTableCellRenderer {
            public CenterRenderer() {
                this.setHorizontalAlignment(SwingConstants.CENTER);
            }
        }

        DefaultTableCellRenderer centerHorizontalAlignment = new CenterRenderer();
        this.networkTable.getColumnModel().getColumn(2).setCellRenderer(centerHorizontalAlignment);
        this.networkTable.getColumnModel().getColumn(3).setCellRenderer(centerHorizontalAlignment);
        
        this.networkTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), null);
        this.networkTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK), null);
        
        Set<AWTKeyStroke> forward = new HashSet<>(this.networkTable.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
        forward.add(KeyStroke.getKeyStroke("TAB"));
        this.networkTable.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forward);
        Set<AWTKeyStroke> backward = new HashSet<>(this.networkTable.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
        backward.add(KeyStroke.getKeyStroke("shift TAB"));
        this.networkTable.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, backward);
        
        final TableCellRenderer tcrOs = this.networkTable.getTableHeader().getDefaultRenderer();
        this.networkTable.getTableHeader().setDefaultRenderer(
            (JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) -> {
                JLabel lbl = (JLabel) tcrOs.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                lbl.setBorder(
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, Color.LIGHT_GRAY),
                        BorderFactory.createEmptyBorder(0, 5, 0, 5)
                    )
                );
                return lbl;
            }
        );
        
        JScrollIndicator scrollerNetwork = new JScrollIndicator(this.networkTable);
        scrollerNetwork.scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, -1, -1));
        scrollerNetwork.scrollPane.setViewportBorder(BorderFactory.createEmptyBorder(0, 0, -1, -1));
        
        AdjustmentListener singleItemScroll = adjustmentEvent -> {
            // The user scrolled the List (using the bar, mouse wheel or something else):
            if (adjustmentEvent.getAdjustmentType() == AdjustmentEvent.TRACK){
                // Jump to the next "block" (which is a row".
                adjustmentEvent.getAdjustable().setBlockIncrement(100);
                adjustmentEvent.getAdjustable().setUnitIncrement(100);
            }
        };

        scrollerNetwork.scrollPane.getVerticalScrollBar().addAdjustmentListener(singleItemScroll);
        scrollerNetwork.scrollPane.getHorizontalScrollBar().addAdjustmentListener(singleItemScroll);
        
        this.network.setLeftComponent(scrollerNetwork);
        
        MouseTabbedPane networkDetailTabs = new MouseTabbedPane();
        networkDetailTabs.setUI(new CustomMetalTabbedPaneUI() {
            @Override protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
                return Math.max(65, super.calculateTabWidth(tabPlacement, tabIndex, metrics));
            }
        });
        networkDetailTabs.addTab(I18n.valueByKey("NETWORK_TAB_URL_LABEL"), new LightScrollPane(1, 1, 0, 0, this.networkTabUrl));
        networkDetailTabs.addTab(I18n.valueByKey("NETWORK_TAB_RESPONSE_LABEL"), new LightScrollPane(1, 1, 0, 0, this.networkTabResponse));
        
        this.networkTabPreview.setEditorKit(new HTMLEditorKitTextPaneWrap());
        
        networkDetailTabs.addTab(I18n.valueByKey("NETWORK_TAB_SOURCE_LABEL"), new LightScrollPane(1, 1, 0, 0, this.networkTabSource));
        networkDetailTabs.addTab(I18n.valueByKey("NETWORK_TAB_PREVIEW_LABEL"), new LightScrollPane(1, 1, 0, 0, this.networkTabPreview));
        networkDetailTabs.addTab(I18n.valueByKey("NETWORK_TAB_HEADERS_LABEL"), new LightScrollPane(1, 1, 0, 0, this.networkTabHeader));
        networkDetailTabs.addTab(I18n.valueByKey("NETWORK_TAB_PARAMS_LABEL"), new LightScrollPane(1, 1, 0, 0, this.networkTabParam));
        
        DefaultCaret caret = (DefaultCaret) this.networkTabResponse.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        caret = (DefaultCaret) this.networkTabSource.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        caret = (DefaultCaret) this.networkTabPreview.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        caret = (DefaultCaret) this.networkTabHeader.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        caret = (DefaultCaret) this.networkTabParam.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        caret = (DefaultCaret) this.networkTabUrl.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        
        this.networkTabHeader.setLineWrap(true);
        this.networkTabParam.setLineWrap(true);
        this.networkTabResponse.setLineWrap(true);
        this.networkTabUrl.setLineWrap(true);
        this.networkTabSource.setLineWrap(true);
        
        this.networkTabPreview.setContentType("text/html");
        this.networkTabPreview.setEditable(false);
        
        this.networkTable.getColumnModel().getColumn(1).setPreferredWidth(500);
        
        this.networkTable.getSelectionModel().addListSelectionListener(event -> {
            // prevent double event
            if (!event.getValueIsAdjusting() && PanelConsoles.this.networkTable.getSelectedRow() > -1) {
                this.changeTextNetwork();
            }
        });

        this.network.setRightComponent(networkDetailTabs);

        MediatorGui.register(new TabConsoles());
        MediatorGui.tabConsoles().setUI(new CustomMetalTabbedPaneUI() {
            @Override protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
                return Math.max(80, super.calculateTabWidth(tabPlacement, tabIndex, metrics));
            }
        });
        MediatorGui.tabConsoles().setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));

        MediatorGui.tabConsoles().addTab(
            "Console",
            HelperUi.ICON_CONSOLE,
            new LightScrollPane(1, 0, 0, 0, this.consoleTextPane.getProxy()),
            I18n.valueByKey("CONSOLE_MAIN_TOOLTIP")
        );
        JLabel labelConsole = new JLabel(I18n.valueByKey("CONSOLE_MAIN_LABEL"), HelperUi.ICON_CONSOLE, SwingConstants.CENTER);
        MediatorGui.tabConsoles().setTabComponentAt(
            MediatorGui.tabConsoles().indexOfTab("Console"),
            labelConsole
        );
        I18n.addComponentForKey("CONSOLE_MAIN_LABEL", labelConsole);

        // Order is important
        Preferences prefs = Preferences.userRoot().node(InjectionModel.class.getName());
        if (prefs.getBoolean(HelperUi.JAVA_VISIBLE, false)) {
            this.insertJavaTab();
        }
        if (prefs.getBoolean(HelperUi.NETWORK_VISIBLE, true)) {
            this.insertNetworkTab();
        }
        if (prefs.getBoolean(HelperUi.CHUNK_VISIBLE, true)) {
            this.insertChunkTab();
        }
        if (prefs.getBoolean(HelperUi.BINARY_VISIBLE, true)) {
            this.insertBooleanTab();
        }

        MediatorGui.tabConsoles().addChangeListener(changeEvent -> {
            JTabbedPane tabs = MediatorGui.tabConsoles();
            if (tabs.getSelectedIndex() > -1) {
                Component currentTabHeader = tabs.getTabComponentAt(tabs.getSelectedIndex());
                if (currentTabHeader != null) {
                    currentTabHeader.setFont(currentTabHeader.getFont().deriveFont(Font.PLAIN));
                    currentTabHeader.setForeground(Color.BLACK);
                }
            }
        });

        this.setLayout(new OverlayLayout(this));

        BasicArrowButton showBottomButton = new BasicArrowButton(SwingConstants.SOUTH);
        showBottomButton.setBorderPainted(false);
        showBottomButton.setPreferredSize(showBottomButton.getPreferredSize());
        showBottomButton.setMaximumSize(showBottomButton.getPreferredSize());
        showBottomButton.setOpaque(false);
        showBottomButton.addActionListener(SplitHorizontalTopBottom.getActionHideShowConsole());

        JPanel arrowDownPanel = new JPanel();
        arrowDownPanel.setLayout(new BorderLayout());
        arrowDownPanel.setOpaque(false);
        // Disable overlap with zerosizesplitter
        arrowDownPanel.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
        arrowDownPanel.setPreferredSize(new Dimension(Integer.MAX_VALUE, 27));
        arrowDownPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
        arrowDownPanel.add(showBottomButton, BorderLayout.LINE_END);
        this.add(arrowDownPanel);
        this.add(MediatorGui.tabConsoles());

        // Do Overlay
        arrowDownPanel.setAlignmentX(FlowLayout.TRAILING);
        arrowDownPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        MediatorGui.tabConsoles().setAlignmentX(FlowLayout.LEADING);
        MediatorGui.tabConsoles().setAlignmentY(Component.TOP_ALIGNMENT);

        this.chunkTextArea.setLineWrap(true);
        this.binaryTextArea.setLineWrap(true);
    }

    /**
     * Add Chunk console to bottom panel.
     */
    public void insertChunkTab() {
        MediatorGui.tabConsoles().insertTab(
            "Chunk",
            HelperUi.ICON_CHUNK,
            new LightScrollPane(1, 0, 0, 0, PanelConsoles.this.chunkTextArea),
            I18n.valueByKey("CONSOLE_CHUNK_TOOLTIP"),
            1
        );

        JLabel labelChunk = new JLabel(I18n.valueByKey("CONSOLE_CHUNK_LABEL"), HelperUi.ICON_CHUNK, SwingConstants.CENTER);
        MediatorGui.tabConsoles().setTabComponentAt(
            MediatorGui.tabConsoles().indexOfTab("Chunk"),
            labelChunk
        );
        I18n.addComponentForKey("CONSOLE_CHUNK_LABEL", labelChunk);
    }

    /**
     * Add Binary console to bottom panel.
     */
    public void insertBooleanTab() {
        MediatorGui.tabConsoles().insertTab(
            "Boolean",
            HelperUi.ICON_BINARY,
            new LightScrollPane(1, 0, 0, 0, PanelConsoles.this.binaryTextArea),
            I18n.valueByKey("CONSOLE_BINARY_TOOLTIP"),
            1 + (MediatorGui.menubar().getChunkMenu().isSelected() ? 1 : 0)
        );

        JLabel labelBoolean = new JLabel(I18n.valueByKey("CONSOLE_BINARY_LABEL"), HelperUi.ICON_BINARY, SwingConstants.CENTER);
        MediatorGui.tabConsoles().setTabComponentAt(
            MediatorGui.tabConsoles().indexOfTab("Boolean"),
            labelBoolean
        );
        I18n.addComponentForKey("CONSOLE_BINARY_LABEL", labelBoolean);
    }

    /**
     * Add Network tab to bottom panel.
     */
    public void insertNetworkTab() {
        MediatorGui.tabConsoles().insertTab(
            "Network",
            HelperUi.ICON_HEADER,
            PanelConsoles.this.network,
            I18n.valueByKey("CONSOLE_NETWORK_TOOLTIP"),
            MediatorGui.tabConsoles().getTabCount() - (MediatorGui.menubar().getJavaDebugMenu().isSelected() ? 1 : 0)
        );

        JLabel labelNetwork = new JLabel(I18n.valueByKey("CONSOLE_NETWORK_LABEL"), HelperUi.ICON_HEADER, SwingConstants.CENTER);
        MediatorGui.tabConsoles().setTabComponentAt(
            MediatorGui.tabConsoles().indexOfTab("Network"),
            labelNetwork
        );
        I18n.addComponentForKey("CONSOLE_NETWORK_LABEL", labelNetwork);
    }

    /**
     * Add Java console to bottom panel.
     */
    public void insertJavaTab() {
        MediatorGui.tabConsoles().insertTab(
            "Java",
            HelperUi.ICON_CUP,
            new LightScrollPane(1, 0, 0, 0, PanelConsoles.this.javaTextPane.getProxy()),
            I18n.valueByKey("CONSOLE_JAVA_TOOLTIP"),
            MediatorGui.tabConsoles().getTabCount()
        );

        JLabel labelJava = new JLabel(I18n.valueByKey("CONSOLE_JAVA_LABEL"), HelperUi.ICON_CUP, SwingConstants.CENTER);
        MediatorGui.tabConsoles().setTabComponentAt(
            MediatorGui.tabConsoles().indexOfTab("Java"),
            labelJava
        );
        I18n.addComponentForKey("CONSOLE_JAVA_LABEL", labelJava);
    }
    
    public void addHeader(HttpHeader header) {
        this.listHttpHeader.add(header);
    }
    
    public void reset() {
        this.listHttpHeader.clear();
        
        // Empty infos tabs
        this.getChunkTab().setText("");
        this.getBinaryTab().setText("");
        
        // Fix #4657, Fix #1860: Multiple Exceptions on setRowCount()
        try {
            ((DefaultTableModel) this.networkTable.getModel()).setRowCount(0);
        } catch(NullPointerException | ArrayIndexOutOfBoundsException e) {
            LOGGER.error(e.getMessage(), e);
        }
        
        this.javaTextPane.getProxy().setText("");
        
        this.networkTabUrl.setText("");
        this.networkTabHeader.setText("");
        this.networkTabParam.setText("");
        this.networkTabResponse.setText("");
        this.networkTabSource.setText("");
        
        // Fix #41879: ArrayIndexOutOfBoundsException on setText()
        try {
            this.networkTabPreview.setText("");
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.error(e, e);
        }
    }
    
    public void changeTextNetwork() {
        HttpHeader networkData = this.listHttpHeader.get(PanelConsoles.this.networkTable.getSelectedRow());
        this.networkTabHeader.setText(networkData.getHeader());
        this.networkTabParam.setText(networkData.getPost());
        this.networkTabUrl.setText(networkData.getUrl());
        
        this.networkTabResponse.setText("");
        for (String key: networkData.getResponse().keySet()) {
            this.networkTabResponse.append(key + ": " + networkData.getResponse().get(key));
            this.networkTabResponse.append("\n");
        }
        
        this.networkTabSource.setText(StringUtil.detectUtf8Html(networkData.getSource()).replaceAll("#{5,}", "#*"));
        
        // Reset EditorKit to disable previous document effect
        this.networkTabPreview.getEditorKit().createDefaultDocument();
        
        // Proxy is used by jsoup to display <img> tags
        // Previous test for 2xx Success and 3xx Redirection was Header only,
        // now get the HTML content
        // Fix #35352: EmptyStackException on setText()
        // Fix #39841: RuntimeException on setText()
        // Fix #42523: ExceptionInInitializerError on clean()
        try {
            this.networkTabPreview.setText(
                Jsoup.clean(
                    "<html>"+ StringUtil.detectUtf8(networkData.getSource()).replaceAll("#{5,}", "#*") + "</html>"
                        .replaceAll("<img.*>", "")
                        .replaceAll("<input.*type=\"?hidden\"?.*>", "")
                        .replaceAll("<input.*type=\"?(submit|button)\"?.*>", "<div style=\"background-color:#eeeeee;text-align:center;border:1px solid black;width:100px;\">button</div>")
                        .replaceAll("<input.*>", "<div style=\"text-align:center;border:1px solid black;width:100px;\">input</div>"),
                    Whitelist.relaxed()
                        .addTags("center", "div", "span")
                        .addAttributes(":all", "style")
                )
            );
        } catch (RuntimeException | ExceptionInInitializerError e) {
            LOGGER.error(e, e);
        }
    }
    
    // Getter and setter

    public JTextArea getChunkTab() {
        return this.chunkTextArea;
    }

    public JSplitPaneWithZeroSizeDivider getNetwork() {
        return this.network;
    }

    public JTextArea getBinaryTab() {
        return this.binaryTextArea;
    }
    
}
