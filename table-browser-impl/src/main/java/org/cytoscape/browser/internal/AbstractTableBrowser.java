package org.cytoscape.browser.internal;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.browser.internal.util.TableColumnStat;
import org.cytoscape.equations.EquationCompiler;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNetworkTableManager;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableManager;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.session.events.SessionAboutToBeSavedEvent;
import org.cytoscape.session.events.SessionAboutToBeSavedListener;
import org.cytoscape.session.events.SessionLoadedEvent;
import org.cytoscape.session.events.SessionLoadedListener;
import org.cytoscape.task.TableTaskFactory;
import org.cytoscape.work.swing.DialogTaskManager;


/**
 * Base class for all Table Browsers.
 *
 */
public abstract class AbstractTableBrowser extends JPanel implements CytoPanelComponent, ActionListener , SessionLoadedListener, SessionAboutToBeSavedListener{

	private static final long serialVersionUID = 1968196123280466989L;
	
	static final Dimension SELECTOR_SIZE = new Dimension(400, 32);
	
	// Color theme for table browser.
	static final Color NETWORK_COLOR = new Color(0xA5, 0x2A, 0x2A);
	static final Color SELECTED_ITEM_BACKGROUND_COLOR = new Color(0xA0, 0xA0, 0xA0, 80);
	
	private static final Dimension PANEL_SIZE = new Dimension(550, 400);
	
	protected final CyTableManager tableManager;
	protected final CyServiceRegistrar serviceRegistrar;
	private final EquationCompiler compiler;
	
	protected AttributeBrowserToolBar attributeBrowserToolBar;
		
	protected CyTable currentTable;
	protected final CyApplicationManager applicationManager;
	protected final CyNetworkManager networkManager;
	private final PopupMenuHelper popupMenuHelper; 
	private final CyEventHelper eventHelper;
	

	// Tab title for the CytoPanel
	private final String tabTitle;
	private final Map<BrowserTableModel,JScrollPane> scrollPanes;
	private final Map<CyTable,BrowserTableModel> browserTableModels;
	private JScrollPane currentScrollPane;
	protected final String appFileName;

	
	AbstractTableBrowser(final String tabTitle,
						 final CyTableManager tableManager,
						 final CyNetworkTableManager networkTableManager,
						 final CyServiceRegistrar serviceRegistrar,
						 final EquationCompiler compiler,
						 final CyNetworkManager networkManager,
						 final TableTaskFactory deleteTableTaskFactory,
						 final DialogTaskManager guiTaskManager,
						 final PopupMenuHelper popupMenuHelper,
						 final CyApplicationManager applicationManager,
						 final CyEventHelper eventHelper) {
		this.tableManager = tableManager;
		this.serviceRegistrar = serviceRegistrar;
		this.compiler = compiler;
		this.tabTitle = tabTitle;
		this.networkManager = networkManager;
		this.applicationManager = applicationManager;
		this.popupMenuHelper = popupMenuHelper;
		this.eventHelper = eventHelper;
		this.appFileName  = tabTitle.replaceAll(" ", "").concat(".props");

		this.scrollPanes = new HashMap<BrowserTableModel,JScrollPane>();
		this.browserTableModels = new HashMap<CyTable,BrowserTableModel>();
		
		this.setLayout(new BorderLayout());
		this.setPreferredSize(PANEL_SIZE);
		this.setSize(PANEL_SIZE);
	}

	/**
	 * Returns the Component to be added to the CytoPanel.
	 * @return The Component to be added to the CytoPanel.
	 */
	@Override
	public Component getComponent() { return this; }

	/**
	 * Returns the name of the CytoPanel that this component should be added to.
	 * @return the name of the CytoPanel that this component should be added to.
	 */
	@Override
	public CytoPanelName getCytoPanelName() {
		return CytoPanelName.SOUTH;
	}

	/**
	 * Returns the title of the tab within the CytoPanel for this component.
	 * @return the title of the tab within the CytoPanel for this component.
	 */
	@Override
	public String getTitle() { return tabTitle; }

	/**
	 * @return null
	 */
	@Override
	public Icon getIcon() { return null; }
	
	// Delete the given table from the JTable
	public void deleteTable(CyTable cyTable){
		BrowserTableModel model = browserTableModels.remove(cyTable);
		if (model == null) {
			return;
		}
		scrollPanes.remove(model);
		serviceRegistrar.unregisterAllServices(model);
		
		model.getBrowserTable().setModel(new DefaultTableModel());
		
		if (currentTable == cyTable) {
			currentTable = null;
		}
	}
	
	synchronized void showSelectedTable() {
		if (currentScrollPane != null)
			remove(currentScrollPane);

		final BrowserTableModel currentBrowserTableModel = getCurrentBrowserTableModel();
		final JScrollPane newScrollPane = getScrollPane(currentBrowserTableModel);
		
		if (newScrollPane != null)
			add(newScrollPane, BorderLayout.CENTER);
		else
			repaint();

		currentScrollPane = newScrollPane;
		applicationManager.setCurrentTable(currentTable);
		attributeBrowserToolBar.setBrowserTableModel(currentBrowserTableModel);
	
		
		/* 
		// Never resize columns as they would reset the columns each time the view is changed
		if (currentBrowserTableModel != null)
			ColumnResizer.adjustColumnPreferredWidths(currentBrowserTableModel.getTable());
		 */
	}

	private JScrollPane getScrollPane(final BrowserTableModel browserTableModel) {
		JScrollPane scrollPane = null;
		
		if (browserTableModel != null) {
			scrollPane = scrollPanes.get(browserTableModel);
			
			if (scrollPane == null) {
				final BrowserTable browserTable = browserTableModel.getBrowserTable(); 
				serviceRegistrar.registerAllServices(browserTableModel, new Properties());
				browserTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
				browserTable.getTableHeader().setBackground(Color.LIGHT_GRAY);
				browserTable.setUpdateComparators(false);
				browserTable.setModel(browserTableModel);
				
				final TableRowSorter<BrowserTableModel> rowSorter = new TableRowSorter<BrowserTableModel>(browserTableModel);
				browserTable.setRowSorter(rowSorter);
				updateColumnComparators(rowSorter, browserTableModel);
				browserTable.setUpdateComparators(true);
				
				//move and hide SUID and selected by default
				final List<String> attrList = browserTableModel.getAllAttributeNames();

				BrowserTableColumnModel columnModel = (BrowserTableColumnModel) browserTable.getColumnModel();
				
				if(attrList.contains(CyNetwork.SUID))
					columnModel.moveColumn(browserTable.convertColumnIndexToView(browserTableModel.mapColumnNameToColumnIndex(CyNetwork.SUID)), 0);
				if(attrList.contains(CyNetwork.SELECTED))
					columnModel.moveColumn(browserTable.convertColumnIndexToView(browserTableModel.mapColumnNameToColumnIndex(CyNetwork.SELECTED)), 1);
				
				attrList.remove(CyNetwork.SUID);
				attrList.remove( CyNetwork.SELECTED);
				browserTableModel.setVisibleAttributeNames(attrList);
				
				scrollPane = new JScrollPane(browserTable);
				scrollPanes.put(browserTableModel, scrollPane);
			}
		}

		return scrollPane;
	}

	protected BrowserTableModel getCurrentBrowserTableModel() {
		BrowserTableModel btm = browserTableModels.get(currentTable);
		
		if (btm == null && currentTable != null) {
			final BrowserTable browserTable = new BrowserTable(compiler, popupMenuHelper,
					applicationManager, eventHelper, tableManager);
			BrowserTableColumnModel columnModel = new BrowserTableColumnModel();
			browserTable.setColumnModel(columnModel);
			
			btm = new BrowserTableModel(browserTable, currentTable, compiler, tableManager);
			browserTableModels.put(currentTable, btm);
		}
		
		return btm;
	}
	
	protected Map<CyTable, BrowserTableModel>  getAllBrowserTablesMap (){
		return browserTableModels;
	}

	void updateColumnComparators(final TableRowSorter<BrowserTableModel> rowSorter,
			final BrowserTableModel browserTableModel) {
		for (int column = 0; column < browserTableModel.getColumnCount(); ++column)
			rowSorter.setComparator(
				column,
				new ValidatedObjectAndEditStringComparator(
					browserTableModel.getColumn(column).getType()));
	}

	@Override
	public String toString() {
		return "AbstractTableBrowser [tabTitle=" + tabTitle + ", currentTable=" + currentTable + "]";
	}
	
	
	@Override
	public void handleEvent(SessionLoadedEvent e) {
		Map<String, TableColumnStat> tscMap = TableColumnStatFileIO.read(e, appFileName);
		
		if (tscMap == null || tscMap.isEmpty())
			return;
		
		Map<CyTable, BrowserTableModel>  browserTableModels = getAllBrowserTablesMap();
		
		for (CyTable table : browserTableModels.keySet()){
			if (! tscMap.containsKey(table.getTitle()))
				continue;
			
			final TableColumnStat tcs = tscMap.get(table.getTitle());
			
			final BrowserTableModel btm = browserTableModels.get(table);
			final BrowserTableColumnModel colM = (BrowserTableColumnModel) btm.getTable().getColumnModel();
			colM.setAllColumnsVisible();
			final List<String> orderedCols = tcs.getOrderedCol();
			final JTable jtable = btm.getTable();
			
			for (int i =0; i< orderedCols.size(); i++){
				final String colName = orderedCols.get(i);
				colM.moveColumn( jtable.convertColumnIndexToView(btm.mapColumnNameToColumnIndex(colName))  , i);
			}
			btm.setVisibleAttributeNames(tcs.getVisibleCols());
			
		}
		
	}

	@Override
	public void handleEvent(SessionAboutToBeSavedEvent e) {

		Map<CyTable, BrowserTableModel>  browserTableModels = getAllBrowserTablesMap();
		List< TableColumnStat> tableColumnStatList = new ArrayList<TableColumnStat>();

		for (CyTable table :  browserTableModels.keySet()){

			TableColumnStat tcs = new TableColumnStat(table.getTitle());

			BrowserTableModel btm = browserTableModels.get(table);
			BrowserTableColumnModel colM = (BrowserTableColumnModel) btm.getTable().getColumnModel();
			List<String> visAttrs = btm.getVisibleAttributeNames();
			colM.setAllColumnsVisible();
			List<String> attrs =  btm.getAllAttributeNames();
			JTable jtable = btm.getTable();

			for (String name: attrs){
				int viewIndex = jtable.convertColumnIndexToView(btm.mapColumnNameToColumnIndex(name));
				tcs.addColumnStat(name, viewIndex,  visAttrs.contains(name));			
			}

			btm.setVisibleAttributeNames(visAttrs);
			tableColumnStatList.add(tcs);
		}
		TableColumnStatFileIO.write(tableColumnStatList, e, this.appFileName );	

	}
}
