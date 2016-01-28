package org.cytoscape.internal.view;

import static javax.swing.GroupLayout.DEFAULT_SIZE;
import static javax.swing.GroupLayout.PREFERRED_SIZE;
import static javax.swing.GroupLayout.Alignment.CENTER;
import static org.cytoscape.internal.view.NetworkViewGrid.MAX_THUMBNAIL_SIZE;
import static org.cytoscape.internal.view.NetworkViewGrid.MIN_THUMBNAIL_SIZE;
import static org.cytoscape.util.swing.IconManager.ICON_CHECK_SQUARE;
import static org.cytoscape.util.swing.IconManager.ICON_EXTERNAL_LINK_SQUARE;
import static org.cytoscape.util.swing.IconManager.ICON_SHARE_ALT_SQUARE;
import static org.cytoscape.util.swing.IconManager.ICON_SQUARE_O;
import static org.cytoscape.util.swing.IconManager.ICON_TH;
import static org.cytoscape.util.swing.IconManager.ICON_THUMB_TACK;
import static org.cytoscape.util.swing.IconManager.ICON_TRASH_O;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.cytoscape.internal.util.ViewUtil;
import org.cytoscape.internal.view.NetworkViewGrid.ThumbnailPanel;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.destroy.DestroyNetworkViewTaskFactory;
import org.cytoscape.util.swing.IconManager;
import org.cytoscape.util.swing.LookAndFeelUtil;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.presentation.RenderingEngine;
import org.cytoscape.view.presentation.RenderingEngineFactory;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.swing.DialogTaskManager;

@SuppressWarnings("serial")
public class NetworkViewMainPanel extends JPanel {

	private static final String GRID_NAME = "__NETWORK_VIEW_GRID__";

	private JPanel contentPane;
	private JPanel toolBarsPanel;
	private JPanel gridToolBar;
	private JPanel viewToolBar;
	private final CardLayout cardLayout;
	private final NetworkViewGrid networkViewGrid;
	private JScrollPane gridScrollPane;
	
	private JButton viewModeButton;
	private JButton selectAllViewsButton;
	private JButton deselectAllViewsButton;
	private JLabel selectionLabel;
	private JButton detachSelectedViewsButton;
	private JButton reattachAllViewsButton;
	private JButton destroySelectedViewsButton;
	private JSlider thumbnailSlider;
	
	private JButton gridModeButton;
	private JButton detachViewButton;
	private JLabel viewTitleLabel;
	private JTextField viewTitleTextField;
	private JButton destroyViewButton;
	
	private final Map<String, NetworkViewContainer> viewContainers;
	private final Map<String, NetworkViewFrame> viewFrames;
	
	private final CytoscapeMenus cyMenus;
	
	private final CyServiceRegistrar serviceRegistrar;

	public NetworkViewMainPanel(final CytoscapeMenus cyMenus, final CyServiceRegistrar serviceRegistrar) {
		this.cyMenus = cyMenus;
		this.serviceRegistrar = serviceRegistrar;
		
		viewContainers = new LinkedHashMap<>();
		viewFrames = new HashMap<>();
		cardLayout = new CardLayout();
		networkViewGrid = new NetworkViewGrid(serviceRegistrar);
		
		init();
	}
	
	public RenderingEngine<CyNetwork> addNetworkView(final CyNetworkView view,
			final RenderingEngineFactory<CyNetwork> engineFactory, boolean showView) {
		if (isRendered(view))
			return null;
		
		final NetworkViewContainer vc = new NetworkViewContainer(view, engineFactory, serviceRegistrar);
		
		vc.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent e) {
				setCurrentNetworkView(view);
				setSelectedNetworkViews(Collections.singletonList(view));
				view.updateView();
			}
		});
		
		viewContainers.put(vc.getName(), vc);
		networkViewGrid.addItem(vc.getRenderingEngine());
		getContentPane().add(vc, vc.getName());
		
		if (showView)
			showViewContainer(vc.getName());
		else
			showGrid();
		
		return vc.getRenderingEngine();
	}
	
	public boolean isRendered(final CyNetworkView view) {
		final String name = ViewUtil.createUniqueKey(view);
		return viewContainers.containsKey(name) || viewFrames.containsKey(name);
	}

	public void remove(final CyNetworkView view) {
		if (view == null)
			return;
		
		RenderingEngine<CyNetwork> re = null;
		final int total = getContentPane().getComponentCount();
		
		for (int i = 0; i < total; i++) {
			final Component c = getContentPane().getComponent(i);
			
			if (c instanceof NetworkViewContainer) {
				final NetworkViewContainer vc = (NetworkViewContainer) c;
				
				if (vc.getNetworkView().equals(view)) {
					cardLayout.removeLayoutComponent(c);
					viewContainers.remove(vc.getName());
					re = vc.getRenderingEngine();
					
					vc.getRootPane().getLayeredPane().removeAll();
					vc.getRootPane().getContentPane().removeAll();
					
					break;
				}
			}
		}
		
		final NetworkViewFrame frame = viewFrames.remove(ViewUtil.createUniqueKey(view));
		
		if (frame != null) {
			re = frame.getRenderingEngine();
			
			frame.getRootPane().getLayeredPane().removeAll();
			frame.getRootPane().getContentPane().removeAll();
			frame.dispose();
			
			for (ComponentListener l : frame.getComponentListeners())
				frame.removeComponentListener(l);
			
			for (WindowListener l : frame.getWindowListeners())
				frame.removeWindowListener(l);
		}
		
		if (re != null) {
			networkViewGrid.removeItems(Collections.singleton(re));
			showGrid();
		}
	}
	
	public void setSelectedNetworkViews(final List<CyNetworkView> networkViews) {
		final Set<ThumbnailPanel> selectedItems = new HashSet<>();
		
		for (ThumbnailPanel tp : networkViewGrid.getItems()) {
			if (networkViews.contains(tp.getNetworkView()))
				selectedItems.add(tp);
		}
			
		networkViewGrid.setSelectedItems(selectedItems);
	}
	
	public List<CyNetworkView> getSelectedNetworkViews() {
		return getNetworkViews(networkViewGrid.getSelectedItems());
	}

	public CyNetworkView getCurrentNetworkView() {
		return networkViewGrid.getCurrentNetworkView();
	}
	
	public void setCurrentNetworkView(final CyNetworkView view) {
		networkViewGrid.setCurrentNetworkView(view);
		
		if (view == null) {
			showGrid();
		} else {
			if (isGridMode()) {
				if (networkViewGrid.getCurrentItem() != null)
					networkViewGrid.scrollRectToVisible(networkViewGrid.getCurrentItem().getBounds());
			} else {
				showViewContainer(ViewUtil.createUniqueKey(view));
			}
		}
	}
	
	public void showGrid() {
		cardLayout.show(getContentPane(), GRID_NAME);
		updateToolBars();
		networkViewGrid.update(getThumbnailSlider().getValue()); // TODO remove it when already updating after view changes
	}
	
	public NetworkViewFrame detachNetworkView(final CyNetworkView view) {
		if (view == null)
			return null;
		
		final NetworkViewContainer vc = getNetworkViewContainer(view);
		final String name = vc.getName();
		
		cardLayout.removeLayoutComponent(vc);
		viewContainers.remove(name);
		
		final NetworkViewFrame frame = new NetworkViewFrame(vc, serviceRegistrar);
		viewFrames.put(name, frame);
		
		if (!LookAndFeelUtil.isAquaLAF())
			frame.setJMenuBar(cyMenus.createDummyMenuBar());
		
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowActivated(WindowEvent e) {
				// This is necessary because the same menu bar is used by other frames, including CytoscapeDesktop
				final JMenuBar menuBar = cyMenus.getJMenuBar();
				final Window window = SwingUtilities.getWindowAncestor(menuBar);

				if (!frame.equals(window)) {
					if (window instanceof JFrame && !LookAndFeelUtil.isAquaLAF()) {
						// Do this first, or the user could see the menu disappearing from the out-of-focus windows
						final JMenuBar dummyMenuBar = cyMenus.createDummyMenuBar();
						((JFrame) window).setJMenuBar(dummyMenuBar);
						dummyMenuBar.updateUI();
						window.repaint();
					}

					frame.setJMenuBar(menuBar);
					menuBar.updateUI();
				}
			}
			@Override
			public void windowClosed(WindowEvent e) {
				reattachNetworkView(view);
			}
			@Override
			public void windowOpened(WindowEvent e) {
				// Add another window listener so subsequent Window Activated events trigger
				// a current view change action.
				// It has to be done this way (after the Open event), otherwise it can cause infinite loops
				// when detaching more than one view at the same time.
				frame.addWindowListener(new WindowAdapter() {
					@Override
					public void windowActivated(WindowEvent e) {
						setCurrentNetworkView(frame.getNetworkView());
						setSelectedNetworkViews(Collections.singletonList(frame.getNetworkView()));
					}
				});
			}
		});
		frame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent e) {
				view.updateView();
			}
			@Override
			public void componentResized(ComponentEvent e) {
				view.setVisualProperty(BasicVisualLexicon.NETWORK_WIDTH, (double)frame.getContentPane().getWidth());
				view.setVisualProperty(BasicVisualLexicon.NETWORK_HEIGHT, (double)frame.getContentPane().getHeight());
				view.updateView();
			};
		});
		
		int w = view.getVisualProperty(BasicVisualLexicon.NETWORK_WIDTH).intValue();
		int h = view.getVisualProperty(BasicVisualLexicon.NETWORK_HEIGHT).intValue();
		final boolean resizable = !view.isValueLocked(BasicVisualLexicon.NETWORK_WIDTH) &&
				!view.isValueLocked(BasicVisualLexicon.NETWORK_HEIGHT);
		
		if (w > 0 && h > 0)
			frame.getContentPane().setPreferredSize(new Dimension(w, h));
		
		getNetworkViewGrid().setDetached(vc.getNetworkView(), true);
		showGrid();
		
		frame.pack();
		frame.setResizable(resizable);
		frame.setVisible(true);
		
		return frame;
	}
	
	public void reattachNetworkView(final CyNetworkView view) {
		final NetworkViewFrame frame = getNetworkViewFrame(view);
		
		if (frame != null) {
			final NetworkViewContainer vc = frame.getNetworkViewContainer();
			final JRootPane rootPane = frame.getRootPane();
			
			frame.dispose();
			viewFrames.remove(vc.getName());
			
			vc.setRootPane(rootPane);
			vc.setDetached(false);
			getContentPane().add(vc, vc.getName());
			viewContainers.put(vc.getName(), vc);
			getNetworkViewGrid().setDetached(vc.getNetworkView(), false);
			showViewContainer(vc.getName());
		}
	}
	
	public void updateThumbnail(final CyNetworkView view) {
		networkViewGrid.updateThumbnail(view);
	}
	
	public void update(final CyNetworkView view) {
		final ThumbnailPanel tp = networkViewGrid.getItem(view);
		
		if (tp != null)
			tp.update();
		
		final NetworkViewFrame frame = getNetworkViewFrame(view);
		
		if (frame != null) {
			// Frame Title
			frame.setTitle(ViewUtil.getTitle(view));
			
			// Frame Size
			final int w = view.getVisualProperty(BasicVisualLexicon.NETWORK_WIDTH).intValue();
			final int h = view.getVisualProperty(BasicVisualLexicon.NETWORK_HEIGHT).intValue();
			final boolean resizable = !view.isValueLocked(BasicVisualLexicon.NETWORK_WIDTH) &&
					!view.isValueLocked(BasicVisualLexicon.NETWORK_HEIGHT);
			
			if (w > 0 && h > 0) {
				if (w != frame.getContentPane().getWidth() && 
					h != frame.getContentPane().getHeight()) {
					frame.getContentPane().setPreferredSize(new Dimension(w, h));
					frame.pack();
				}
			}
			
			frame.setResizable(resizable);
		} else if (!isGridMode()) {
			final NetworkViewContainer vc = getNetworkViewContainer(view);
			
			if (vc != null && vc.equals(getCurrentViewContainer()))
				updateViewToolBar(vc);
		}
	}
	
	public boolean isEmpty() {
		return viewFrames.isEmpty() && viewContainers.isEmpty();
	}
	
	public NetworkViewGrid getNetworkViewGrid() {
		return networkViewGrid;
	}
	
	public Set<NetworkViewFrame> getAllNetworkViewFrames() {
		return new HashSet<>(viewFrames.values());
	}
	
	private void showViewContainer(final CyNetworkView view) {
		if (view != null)
			showViewContainer(ViewUtil.createUniqueKey(view));
	}
	
	private void showViewContainer(final String name) {
		if (name != null) {
			if (viewContainers.containsKey(name)) {
				cardLayout.show(getContentPane(), name);
				updateToolBars();
			} 
		} else {
			showGrid();
		}
	}

	private void showViewFrame(final NetworkViewFrame frame) {
		frame.setVisible(true);
		frame.toFront();
		showGrid();
	}
	
	private boolean isGridMode() {
		return getCurrentCard() == getGridScrollPane();
	}
	
	private void updateToolBars() {
		final Component currentCard = getCurrentCard();
		
		if (currentCard instanceof NetworkViewContainer) {
			getGridToolBar().setVisible(false);
			getViewToolBar().setVisible(true);
			updateViewToolBar((NetworkViewContainer) currentCard);
		} else {
			getViewToolBar().setVisible(false);
			getGridToolBar().setVisible(true);
			updateGridToolBar();
		}
	}

	private void updateGridToolBar() {
		final Collection<ThumbnailPanel> items = networkViewGrid.getItems();
		final List<ThumbnailPanel> selectedItems = networkViewGrid.getSelectedItems();
		
		getViewModeButton().setEnabled(!items.isEmpty());
		getDestroySelectedViewsButton().setEnabled(!selectedItems.isEmpty());
		
		getSelectAllViewsButton().setEnabled(selectedItems.size() < items.size());
		getDeselectAllViewsButton().setEnabled(!selectedItems.isEmpty());
		
		getDetachSelectedViewsButton().setEnabled(!selectedItems.isEmpty());
		getReattachAllViewsButton().setEnabled(!viewFrames.isEmpty());
		
		if (items.isEmpty())
			getSelectionLabel().setText(null);
		else
			getSelectionLabel().setText(
					selectedItems.size() + " of " + 
							items.size() + " Network View" + (items.size() == 1 ? "" : "s") +
							" selected");
		
		getGridToolBar().updateUI();
	}

	private void updateViewToolBar(final NetworkViewContainer vc) {
		getViewTitleLabel().setText(ViewUtil.getTitle(vc.getNetworkView()));
		getViewToolBar().updateUI();
	}

	private NetworkViewContainer getCurrentViewContainer() {
		final Component c = getCurrentCard();

		return c instanceof NetworkViewContainer ? (NetworkViewContainer) c : null;
	}
	
	private Component getCurrentCard() {
		Component current = null;
		
		for (Component comp : getContentPane().getComponents()) {
			if (comp.isVisible())
				current = comp;
		}
		
		return current;
	}
	
	private void init() {
		setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, UIManager.getColor("Separator.foreground")));
		
		setLayout(new BorderLayout());
		add(getContentPane(), BorderLayout.CENTER);
		add(getToolBarsPanel(), BorderLayout.SOUTH);
		
		// Add Listeners
		networkViewGrid.addPropertyChangeListener("thumbnailPanels", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent e) {
				updateGridToolBar();
				
				for (ThumbnailPanel tp : networkViewGrid.getItems()) {
					tp.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseClicked(MouseEvent e) {
							if (e.getClickCount() == 2) {
								// Double-Click: set this one as current and show attached view or view frame
								final NetworkViewFrame frame = getNetworkViewFrame(tp.getNetworkView());
									
								if (frame != null)
									showViewFrame(frame);
								else
									showViewContainer(tp.getNetworkView());
							}
						}
					});
					tp.addComponentListener(new ComponentAdapter() {
						@Override
						public void componentResized(ComponentEvent e) {
							networkViewGrid.updateThumbnail(tp.getNetworkView());
						};
					});
				}
			}
		});
		
		networkViewGrid.addPropertyChangeListener("selectedItems", new PropertyChangeListener() {
			@Override
			@SuppressWarnings("unchecked")
			public void propertyChange(PropertyChangeEvent e) {
				updateGridToolBar();
				
				firePropertyChange("selectedNetworkViews",
						getNetworkViews((Collection<ThumbnailPanel>) e.getOldValue()),
						getNetworkViews((Collection<ThumbnailPanel>) e.getNewValue()));
			}
		});
		
		// Update
		updateToolBars();
		showGrid();
	}
	
	private JPanel getContentPane() {
		if (contentPane == null) {
			contentPane = new JPanel();
			contentPane.setLayout(cardLayout);
			// Add the first panel in the card layout
			contentPane.add(getGridScrollPane(), GRID_NAME);
		}
		
		return contentPane;
	}
	
	private JPanel getToolBarsPanel() {
		if (toolBarsPanel == null) {
			toolBarsPanel = new JPanel(new BorderLayout());
			
			toolBarsPanel.setBorder(
					BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")));
			
			toolBarsPanel.add(getGridToolBar(), BorderLayout.NORTH);
			toolBarsPanel.add(getViewToolBar(), BorderLayout.SOUTH);
		}
		
		return toolBarsPanel;
	}
	
	private JPanel getGridToolBar() {
		if (gridToolBar == null) {
			gridToolBar = new JPanel();
			gridToolBar.setName("gridToolBar");
			
			final JSeparator sep = new JSeparator(JSeparator.VERTICAL);
			
			final GroupLayout layout = new GroupLayout(gridToolBar);
			gridToolBar.setLayout(layout);
			layout.setAutoCreateContainerGaps(false);
			layout.setAutoCreateGaps(true);
			
			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addContainerGap()
					.addComponent(getViewModeButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getSelectAllViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getDeselectAllViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getDetachSelectedViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getReattachAllViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addGap(0, 10, Short.MAX_VALUE)
					.addComponent(getSelectionLabel(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addGap(0, 10, Short.MAX_VALUE)
					.addComponent(getDestroySelectedViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(sep, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getThumbnailSlider(), 100, 100, 100)
					.addContainerGap()
			);
			layout.setVerticalGroup(layout.createParallelGroup(CENTER, true)
					.addComponent(getViewModeButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getSelectAllViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getDeselectAllViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getDetachSelectedViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getReattachAllViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getSelectionLabel(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getDestroySelectedViewsButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(sep, DEFAULT_SIZE, DEFAULT_SIZE, Short.MAX_VALUE)
					.addComponent(getThumbnailSlider(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
			);
		}
		
		return gridToolBar;
	}
	
	private JPanel getViewToolBar() {
		if (viewToolBar == null) {
			viewToolBar = new JPanel();
			viewToolBar.setName("viewToolBar");
			
			final JSeparator sep = new JSeparator(JSeparator.VERTICAL);
			
			final GroupLayout layout = new GroupLayout(viewToolBar);
			viewToolBar.setLayout(layout);
			layout.setAutoCreateContainerGaps(false);
			layout.setAutoCreateGaps(true);
			
			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addContainerGap()
					.addComponent(getGridModeButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getDetachViewButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(sep, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getViewTitleLabel(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getViewTitleTextField(), 100, 260, 320)
					.addGap(0, 10, Short.MAX_VALUE)
					.addComponent(getDestroyViewButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addContainerGap()
			);
			layout.setVerticalGroup(layout.createParallelGroup(CENTER, true)
					.addComponent(getGridModeButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getDetachViewButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(sep, DEFAULT_SIZE, DEFAULT_SIZE, Short.MAX_VALUE)
					.addComponent(getViewTitleLabel(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getViewTitleTextField(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getDestroyViewButton(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
			);
		}
		
		return viewToolBar;
	}
	
	private JScrollPane getGridScrollPane() {
		if (gridScrollPane == null) {
			gridScrollPane = new JScrollPane(networkViewGrid,
					JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			gridScrollPane.setName(GRID_NAME);
			gridScrollPane.getViewport().setBackground(networkViewGrid.getBackground());
			
			gridScrollPane.getViewport().addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(final MouseEvent e) {
					if (!e.isPopupTrigger())
						networkViewGrid.deselectAll();
				}
			});
		}
		
		return gridScrollPane;
	}
	
	private JButton getSelectAllViewsButton() {
		if (selectAllViewsButton == null) {
			selectAllViewsButton = new JButton(ICON_CHECK_SQUARE + " " + ICON_CHECK_SQUARE);
			selectAllViewsButton.setToolTipText("Select All Network Views");
			styleButton(selectAllViewsButton, serviceRegistrar.getService(IconManager.class).getIconFont(11.0f));

			selectAllViewsButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					networkViewGrid.selectAll();
				}
			});
		}

		return selectAllViewsButton;
	}
	
	private JButton getDeselectAllViewsButton() {
		if (deselectAllViewsButton == null) {
			deselectAllViewsButton = new JButton(ICON_SQUARE_O + " " + ICON_SQUARE_O);
			deselectAllViewsButton.setToolTipText("Deselect All Network Views");
			styleButton(deselectAllViewsButton, serviceRegistrar.getService(IconManager.class).getIconFont(11.0f));

			deselectAllViewsButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					networkViewGrid.deselectAll();
				}
			});
		}

		return deselectAllViewsButton;
	}
	
	private JButton getViewModeButton() {
		if (viewModeButton == null) {
			viewModeButton = new JButton(ICON_SHARE_ALT_SQUARE);
			viewModeButton.setToolTipText("Show Network View");
			styleButton(viewModeButton, serviceRegistrar.getService(IconManager.class).getIconFont(22.0f));
			
			viewModeButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					final CyNetworkView currentView = getCurrentNetworkView();
					
					if (currentView != null) {
						showViewContainer(currentView);
					} else {
						final List<ThumbnailPanel> selectedItems = networkViewGrid.getSelectedItems();
						
						if (!selectedItems.isEmpty())
							showViewContainer(selectedItems.get(0).getNetworkView());
						else if (!networkViewGrid.isEmpty())
							showViewContainer(networkViewGrid.firstItem().getNetworkView());
					}
				}
			});
		}
		
		return viewModeButton;
	}
	
	private JButton getDetachViewButton() {
		if (detachViewButton == null) {
			detachViewButton = new JButton(ICON_EXTERNAL_LINK_SQUARE);
			detachViewButton.setToolTipText("Detach Network View");
			styleButton(detachViewButton, serviceRegistrar.getService(IconManager.class).getIconFont(22.0f));
			
			detachViewButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if (getCurrentViewContainer() != null)
						detachNetworkView(getCurrentNetworkView());
				}
			});
		}
		
		return detachViewButton;
	}
	
	private JButton getDetachSelectedViewsButton() {
		if (detachSelectedViewsButton == null) {
			detachSelectedViewsButton = new JButton(ICON_EXTERNAL_LINK_SQUARE);
			detachSelectedViewsButton.setToolTipText("Detach Selected Network Views");
			styleButton(detachSelectedViewsButton, serviceRegistrar.getService(IconManager.class).getIconFont(22.0f));
			
			detachSelectedViewsButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					final List<ThumbnailPanel> selectedItems = networkViewGrid.getSelectedItems();
					
					if (selectedItems != null) {
						// Get the current view first
						final CyNetworkView currentView = getCurrentNetworkView();
						
						// Detach the views
						for (ThumbnailPanel tp : selectedItems) {
							if (getNetworkViewContainer(tp.getNetworkView()) != null)
								detachNetworkView(tp.getNetworkView());
						}
						
						// Set the original current view by bringing its frame to front, if it is detached
						final NetworkViewFrame frame = getNetworkViewFrame(currentView);
						
						if (frame != null)
							frame.toFront();
					}
				}
			});
		}
		
		return detachSelectedViewsButton;
	}
	
	private JButton getReattachAllViewsButton() {
		if (reattachAllViewsButton == null) {
			reattachAllViewsButton = new JButton(ICON_THUMB_TACK + " " + ICON_THUMB_TACK);
			reattachAllViewsButton.setToolTipText("Reattach All Network Views");
			styleButton(reattachAllViewsButton, serviceRegistrar.getService(IconManager.class).getIconFont(14.0f));
			
			reattachAllViewsButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					final Collection<NetworkViewFrame> allFrames = new ArrayList<>(viewFrames.values());
					
					for (NetworkViewFrame f : allFrames)
						reattachNetworkView(f.getNetworkView());
				}
			});
		}
		
		return reattachAllViewsButton;
	}
	
	private JButton getGridModeButton() {
		if (gridModeButton == null) {
			gridModeButton = new JButton(ICON_TH);
			gridModeButton.setToolTipText("Show Thumbnails");
			styleButton(gridModeButton, serviceRegistrar.getService(IconManager.class).getIconFont(22.0f));
			
			gridModeButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					showGrid();
				}
			});
		}
		
		return gridModeButton;
	}
	
	private JLabel getViewTitleLabel() {
		if (viewTitleLabel == null) {
			viewTitleLabel = new JLabel();
			viewTitleLabel.setToolTipText("Click to change the title...");
			viewTitleLabel.setFont(viewTitleLabel.getFont().deriveFont(LookAndFeelUtil.getSmallFontSize()));
			viewTitleLabel.setMinimumSize(new Dimension(viewTitleLabel.getPreferredSize().width,
					getViewTitleTextField().getPreferredSize().height));
			viewTitleLabel.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					showViewTitleEditor();
				}
				@Override
				public void mouseEntered(MouseEvent e) {
					viewTitleLabel.setForeground(UIManager.getColor("Focus.color"));
				}
				@Override
				public void mouseExited(MouseEvent e) {
					viewTitleLabel.setForeground(UIManager.getColor("Label.foreground"));
				}
			});
		}
		
		return viewTitleLabel;
	}
	
	public JTextField getViewTitleTextField() {
		if (viewTitleTextField == null) {
			viewTitleTextField = new JTextField();
			viewTitleTextField.putClientProperty("JComponent.sizeVariant", "mini"); // Aqua (Mac OS X) only
			viewTitleTextField.setVisible(false);
			viewTitleTextField.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					changeCurrentViewTitle(viewTitleTextField.getText());
				}
			});
			viewTitleTextField.addFocusListener(new FocusAdapter() {
				@Override
				public void focusLost(FocusEvent e) {
					changeCurrentViewTitle(viewTitleTextField.getText());
				}
			});
		}
		
		return viewTitleTextField;
	}
	
	private JButton getDestroyViewButton() {
		if (destroyViewButton == null) {
			destroyViewButton = new JButton(ICON_TRASH_O);
			destroyViewButton.setToolTipText("Destroy Network View");
			styleButton(destroyViewButton, serviceRegistrar.getService(IconManager.class).getIconFont(22.0f));
			
			destroyViewButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					final NetworkViewContainer vc = getCurrentViewContainer();
					
					if (vc != null) {
						final DialogTaskManager taskMgr = serviceRegistrar.getService(DialogTaskManager.class);
						final DestroyNetworkViewTaskFactory taskFactory =
								serviceRegistrar.getService(DestroyNetworkViewTaskFactory.class);
						taskMgr.execute(taskFactory.createTaskIterator(Collections.singleton(vc.getNetworkView())));
					}
				}
			});
		}
		
		return destroyViewButton;
	}
	
	private JButton getDestroySelectedViewsButton() {
		if (destroySelectedViewsButton == null) {
			destroySelectedViewsButton = new JButton(ICON_TRASH_O);
			destroySelectedViewsButton.setToolTipText("Destroy Selected Network Views");
			styleButton(destroySelectedViewsButton, serviceRegistrar.getService(IconManager.class).getIconFont(22.0f));
			
			destroySelectedViewsButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					final List<CyNetworkView> selectedViews = getSelectedNetworkViews();
					
					if (selectedViews != null && !selectedViews.isEmpty()) {
						final DialogTaskManager taskMgr = serviceRegistrar.getService(DialogTaskManager.class);
						final DestroyNetworkViewTaskFactory taskFactory =
								serviceRegistrar.getService(DestroyNetworkViewTaskFactory.class);
						taskMgr.execute(taskFactory.createTaskIterator(selectedViews));
					}
				}
			});
		}
		
		return destroySelectedViewsButton;
	}
	
	private JLabel getSelectionLabel() {
		if (selectionLabel == null) {
			selectionLabel = new JLabel();
			selectionLabel.setHorizontalAlignment(JLabel.CENTER);
			selectionLabel.setFont(selectionLabel.getFont().deriveFont(LookAndFeelUtil.getSmallFontSize()));
		}
		
		return selectionLabel;
	}
	
	private JSlider getThumbnailSlider() {
		if (thumbnailSlider == null) {
			final int value = Math.round(MIN_THUMBNAIL_SIZE + (MAX_THUMBNAIL_SIZE - MIN_THUMBNAIL_SIZE) / 3.0f);
			thumbnailSlider = new JSlider(MIN_THUMBNAIL_SIZE, MAX_THUMBNAIL_SIZE, value);
			thumbnailSlider.setToolTipText("Thumbnail Size");
			thumbnailSlider.putClientProperty("JComponent.sizeVariant", "mini"); // Aqua (Mac OS X) only
			
			thumbnailSlider.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					if (!thumbnailSlider.getValueIsAdjusting()) {
						final int thumbSize = thumbnailSlider.getValue();
						networkViewGrid.update(thumbSize);
					}
				}
			});
		}
		
		return thumbnailSlider;
	}
	
	private NetworkViewContainer getNetworkViewContainer(final CyNetworkView view) {
		return view != null ? viewContainers.get(ViewUtil.createUniqueKey(view)) : null;
	}
	
	private NetworkViewFrame getNetworkViewFrame(final CyNetworkView view) {
		return view != null ? viewFrames.get(ViewUtil.createUniqueKey(view)) : null;
	}
	
	private void showViewTitleEditor() {
		getViewTitleTextField().setText(getViewTitleLabel().getText());
		getViewTitleLabel().setVisible(false);
		getViewTitleTextField().setVisible(true);
		getViewTitleTextField().requestFocusInWindow();
	}
	
	private void changeCurrentViewTitle(String text) {
		if (text != null) {
			text = text.trim();
			
			// TODO Make sure it's unique
			if (!text.isEmpty()) {
				getViewTitleLabel().setText(text);
				// TODO This will fire a ViewChangedEvent - Just let the NetworkViewManager ask this panel to update itself instead?
				getCurrentNetworkView().setVisualProperty(BasicVisualLexicon.NETWORK_TITLE, text);
				
				final ThumbnailPanel tp = networkViewGrid.getCurrentItem();
				
				if (tp != null)
					tp.update();
			}
		}
		
		getViewTitleTextField().setText(null);
		getViewTitleTextField().setVisible(false);
		getViewTitleLabel().setVisible(true);
		getViewToolBar().updateUI();
	}
	
	private static List<CyNetworkView> getNetworkViews(final Collection<ThumbnailPanel> thumbnailPanels) {
		final List<CyNetworkView> views = new ArrayList<>();
		
		for (ThumbnailPanel tp : thumbnailPanels)
			views.add(tp.getNetworkView());
		
		return views;
	}
	
	static void styleButton(final AbstractButton btn, final Font font) {
		btn.setFont(font);
		btn.setBorder(null);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setPreferredSize(new Dimension(32, 32));
	}
}
