package com.notlob.jgrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.ToolTip;

import com.notlob.jgrid.input.GridKeyboardHandler;
import com.notlob.jgrid.input.GridMouseHandler;
import com.notlob.jgrid.listeners.IGridListener;
import com.notlob.jgrid.model.Column;
import com.notlob.jgrid.model.GridModel;
import com.notlob.jgrid.model.Row;
import com.notlob.jgrid.model.RowCountScope;
import com.notlob.jgrid.model.Viewport;
import com.notlob.jgrid.model.filtering.Filter;
import com.notlob.jgrid.providers.IGridContentProvider;
import com.notlob.jgrid.providers.IGridLabelProvider;
import com.notlob.jgrid.providers.IGridToolTipProvider;
import com.notlob.jgrid.providers.IRowProvider;
import com.notlob.jgrid.renderer.GridRenderer;
import com.notlob.jgrid.renderer.animation.RowAnimation;
import com.notlob.jgrid.styles.StyleRegistry;
import com.notlob.jgrid.util.ResourceManager;

public class Grid<T> extends Composite {
		
	// Affects the rending of the selection region rather than how the selection model works.
	public enum SelectionStyle {
		ROW_BASED,
		SINGLE_COLUMN_BASED,
		SINGLE_CELL_BASED,
		SINGLE_ROW_BASED,
		MULTI_COLUMN_BASED
	};
	
	public enum GroupRenderStyle {
		INLINE,
		COLUMN_BASED
	}
	
	// Models.
	protected final GridModel<T> gridModel;
	protected IGridLabelProvider<T> labelProvider;
	protected IGridContentProvider<T> contentProvider;
	protected GridRenderer<T> gridRenderer;
	protected final Viewport<T> viewport;

	// Things we listen to.
	protected final ScrollListener scrollListener;
	protected final ResizeListener resizeListener;
	protected final DisposeListener disposeListener;
	protected final FocusListener focusListener;

	// Keyboard and mouse input handling.
	protected GridMouseHandler<T> mouseHandler;
	protected GridKeyboardHandler<T> keyboardHandler;

	// The grid monitors the internal model for certain events.
	protected GridModel.IModelListener<T> modelListener;
		
	// Things that listen to the grid.
	protected final Collection<IGridListener<T>> listeners;

	// Used for dimension calculations.
	protected final GC gc;
	protected final Point computedArea;
	
	// Used to dispose graphical UI resources managed by this grid.
	protected final ResourceManager resourceManager;

	protected IGridToolTipProvider<T> toolTipProvider;
	protected final ToolTip toolTip;
	protected String emptyMessage;
	protected String emptyFilterMessage;

	// Some grid behavioural flags.	
	protected boolean highlightHoveredRow = true;
	protected boolean highlightAnchorInHeaders = true;
	protected boolean highlightAnchorCellBorder = true;
	protected boolean sortingEnabled = true;
	protected boolean columnMovingEnabled = true;
	
	// Paints some diagnostic details.
	protected boolean debugPainting = false;
		
	// Animate new/update rows?
	protected RowAnimation<T> newRowAnimiation = null;
	protected RowAnimation<T> updatedRowAnimiation = null;
	
	public Grid(final Composite parent) {
		super(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.DOUBLE_BUFFERED /*| SWT.NO_BACKGROUND | SWT.NO_REDRAW_RESIZE*/);
		resourceManager = new ResourceManager(parent.getDisplay());
		gc = new GC(this);
		computedArea = new Point(-1, -1);
		gridModel = new GridModel<T>(this, resourceManager, gc);
		modelListener = new GridModelListener();
		gridModel.addListener(modelListener);
		viewport = new Viewport<T>(this);
		gridRenderer = new GridRenderer<T>(this);
		disposeListener = new GridDisposeListener();
		resizeListener = new ResizeListener();
		scrollListener = new ScrollListener();
		focusListener = new GridFocusListener();
		listeners = new ArrayList<>();
		toolTip = new ToolTip(parent.getShell(), SWT.NONE);
		toolTip.setAutoHide(true);
		keyboardHandler = createKeyboardHandler(gc);
		mouseHandler = createMouseHandler(gc, listeners, toolTip);		

		parent.addDisposeListener(disposeListener);
		setKeyboardHandler(keyboardHandler);
		setMouseHandler(mouseHandler);
		addPaintListener(gridRenderer);
		addListener(SWT.Resize, resizeListener);
		addFocusListener(focusListener);
		getVerticalBar().addSelectionListener(scrollListener);
		getHorizontalBar().addSelectionListener(scrollListener);
	}
	
	@Override
	public void dispose() {
		toolTip.dispose();

		// Remove listeners.
		removeKeyListener(keyboardHandler);
		removeMouseListener(mouseHandler);
		removeMouseTrackListener(mouseHandler);
		removeMouseMoveListener(mouseHandler);
		removeFocusListener(focusListener);
		removePaintListener(gridRenderer);
		removeListener(SWT.Resize, resizeListener);
		getVerticalBar().removeSelectionListener(scrollListener);
		getHorizontalBar().removeSelectionListener(scrollListener);
		gridModel.removeListener(modelListener);
		gridRenderer.dispose();

		// Dispose of UI handles.
		gc.dispose();
		resourceManager.dispose();
		super.dispose();
	}
	
	/**
	 * Testing method to ensure our rows are indexed correctly.
	 */
	public boolean checkIndexes() {
		checkWidget();
		boolean invalid = false;
		
		int index = 0;
		for (Row<T> row : gridModel.getRows()) {
			if (row.getRowIndex() != index) {
				invalid |= true;
				System.out.println(String.format("Row at index [%s] has incorrect cached index [%s] for element [%s]", index, row.getRowIndex(), contentProvider.getElementId(row.getElement())));
			}
			
			index++;
		}
		
		return !invalid;
	}
	
	public void setDebugPainting(boolean debugPainting) {
		this.debugPainting = debugPainting;
	}
	
	public boolean isDebugPainting() {
		return debugPainting;
	}
	
	/**
	 * Note: INTERNAL USE ONLY.
	 */
	public GridModel<T> getGridModel() {
		checkWidget();
		return gridModel;
	}
	
	public GC getGC() {
		return gc;
	}
	
	public ResourceManager getResourceManager() {
		return resourceManager;
	}
	
	protected GridKeyboardHandler<T> createKeyboardHandler(final GC gc) {
		return new GridKeyboardHandler<T>(this, gc);
	}
	
	protected GridMouseHandler<T> createMouseHandler(final GC gc, final Collection<IGridListener<T>> listeners, final ToolTip toolTip) {
		return new GridMouseHandler<T>(this, gc, listeners, toolTip);
	}
	
	// TODO: Duplicate this for the keyboard handler.
	public void setMouseHandler(GridMouseHandler<T> mouseHandler) {
		final GridMouseHandler<T> oldHandler = this.mouseHandler;
		removeMouseListener(oldHandler);
		removeMouseTrackListener(oldHandler);
		removeMouseMoveListener(oldHandler);
		
		this.mouseHandler = mouseHandler;
		addMouseListener(mouseHandler);
		addMouseMoveListener(mouseHandler);
		addMouseTrackListener(mouseHandler);
	}
	
	public void setKeyboardHandler(GridKeyboardHandler<T> keyboardHandler) {
		final GridKeyboardHandler<T> oldHandler = this.keyboardHandler;
		removeKeyListener(oldHandler);
		
		this.keyboardHandler = keyboardHandler;
		addKeyListener(keyboardHandler);
	}
	
	public void enableEvents(final boolean enable) {
		checkWidget();
		gridModel.enableEvents(enable);
	}
	
	public boolean isEventsSuppressed() {
		checkWidget();
		return gridModel.isEventsSuppressed();
	}
	
	public void setHighlightHoveredRow(final boolean highlightHoveredRow) {
		checkWidget();
		this.highlightHoveredRow = highlightHoveredRow;
	}

	public boolean isHighlightHoveredRow() {
		checkWidget();
		return highlightHoveredRow;
	}

	public void setHighlightAnchorInHeaders(final boolean highlightAnchorInHeaders) {
		checkWidget();
		this.highlightAnchorInHeaders = highlightAnchorInHeaders;
	}

	public boolean isHighlightAnchorInHeaders() {
		checkWidget();
		return highlightAnchorInHeaders;
	}

	public boolean isHighlightAnchorCellBorder() {
		checkWidget();
		return highlightAnchorCellBorder;
	}
	
	public void setAnimateNewRows(final RowAnimation<T> rowAnimation) {
		checkWidget();
		this.newRowAnimiation = rowAnimation;
	}
	
	public RowAnimation<T> getAnimiateNewRows() {
		checkWidget();
		return newRowAnimiation;
	}
	
	public void setAnimateUpdatedRows(final RowAnimation<T> rowAnimation) {
		checkWidget();
		this.updatedRowAnimiation = rowAnimation;
	}
	
	public RowAnimation<T> getAnimiateUpdatedRows() {
		checkWidget();
		return updatedRowAnimiation;
	}

	public void setHighlightAnchorCellBorder(final boolean highlightAnchorCellBorder) {
		checkWidget();
		this.highlightAnchorCellBorder = highlightAnchorCellBorder;
	}
	
	public boolean isSelectGroupIfAllChildrenSelected() {
		checkWidget();
		return gridModel.getSelectionModel().isSelectGroupIfAllChildrenSelected();
	}
	
	public void setSelectGroupIfAllChildrenSelected(boolean selectGroupIfAllChildrenSelected) {
		checkWidget();
		gridModel.getSelectionModel().setSelectGroupIfAllChildrenSelected(selectGroupIfAllChildrenSelected);
	}
	
	public void setSelectNextOnRemove(final boolean selectNextOnRemove) {
		checkWidget();
		gridModel.getSelectionModel().setSelectNextOnRemove(selectNextOnRemove);
	}
	
	public boolean isSelectNextOnRemove() {
		checkWidget();
		return gridModel.getSelectionModel().isSelectNextOnRemove();
	}
	
	public SelectionStyle getSelectionStyle() {
		checkWidget();
		return gridModel.getSelectionModel().getSelectionStyle();
	}
	
	public void setSelectionStyle(SelectionStyle selectionStyle) {
		checkWidget();
		gridModel.getSelectionModel().setSelectionStyle(selectionStyle);
	}
	
	public boolean isSortedEnabled() {
		checkWidget();
		return sortingEnabled;
	}
	
	public void setSortedEnabled(final boolean sortingEnabled) {
		checkWidget();
		this.sortingEnabled = sortingEnabled;
	}
	
	public boolean isColumnMovingEnabled() {
		return this.columnMovingEnabled;
	}
	
	public void setColumnMovingEnabled(final boolean columnMovingEnabled) {
		this.columnMovingEnabled = columnMovingEnabled;
	}

	public GroupRenderStyle getGroupRenderStyle() {
		checkWidget();
		return gridModel.getGroupRenderStyle();
	}
	
	public void setGroupRenderStyle(final GroupRenderStyle groupRenderStyle) {
		checkWidget();
		gridModel.setGroupRenderStyle(groupRenderStyle);
	}
	
	public void setShowColumnHeaders(final boolean showColumnHeaders) {
		checkWidget();
		gridModel.setShowColumnHeaders(showColumnHeaders);		
	}
	
	public boolean isShowColumnHeaders() {
		checkWidget();
		return gridModel.isShowColumnHeaders();
	}
	
	public StyleRegistry<T> getStyleRegistry() {
		checkWidget();
		return gridModel.getStyleRegistry();
	}

	public void clearColumns() {
		checkWidget();
		gridModel.clearColumns();
	}
	
	public void addColumns(final List<Column> columns) {
		checkWidget();
		gridModel.addColumns(columns);
	}

	public List<Column> getColumns() {
//		checkWidget();
		return gridModel.getColumns();
	}
	
	public List<Column> getAllColumns() {
//		checkWidget();
		return gridModel.getAllColumns();
	}

	public Column getColumnById(final String columnId) {
//		checkWidget();
		return gridModel.getColumnById(columnId);
	}
	
	public void rebuildVisibleColumns() {
		checkWidget();
		gridModel.rebuildVisibleColumns();
	}
	
	public void pinColumn(final Column column) {
		checkWidget();
		gridModel.pinColumn(column);
	}
	
	public void unpinColumn(final Column column) {
		checkWidget();
		gridModel.unpinColumn(column);
	}

	public void groupBy(final List<Column> columns) {
		checkWidget();
		gridModel.groupBy(columns);
	}
	
	public void ungroupBy(final List<Column> columns) {
		checkWidget();
		gridModel.ungroupBy(columns);
	}

	public List<Column> getGroupByColumns() {
		checkWidget();
		return gridModel.getGroupByColumns();
	}
	
	public Column getGroupColumn(final int columnIndex) {
		checkWidget();
		return gridModel.getGroupByColumns().get(columnIndex);
	}
	
	public Row<T> getColumnHeaderRow() {
		checkWidget();
		return gridModel.getColumnHeaderRow();
	}
	
	public Column getRowNumberColumn() {
		checkWidget();
		return gridModel.getRowNumberColumn();
	}
	
	public Column getGroupSelectorColumn() {
		checkWidget();
		return gridModel.getGroupSelectorColumn();
	}

	public void addElements(final Collection<T> elements) {
		checkWidget();
		
		final Collection<Row<T>> rowsAdded = gridModel.addElements(elements);
		animateIfRequired(rowsAdded, newRowAnimiation);
	}

	public void removeElements(final Collection<T> elements) {
		checkWidget();
		gridModel.removeElements(elements);
	}

	public void updateElements(final Collection<T> elements, final boolean allowAnimation) {
		checkWidget();
		
		final Collection<Row<T>> rowsUpdated = gridModel.updateElements(elements);
		
		if (allowAnimation) {
			animateIfRequired(rowsUpdated, updatedRowAnimiation);
		}
	}
	
	private void animateIfRequired(final Collection<Row<T>> rows, final RowAnimation<T> animation) {
		boolean animationRequired = false;
		
		//
		// Begin animating any rows if configured to do so.
		//
		if (animation != null) { 
			for (Row<T> row : rows) {
				if (row.getAnimation() == null) {
					row.setAnimation(animation);
					row.setFrame(0);					
					animationRequired = true;
				}
			}
			
			if (animationRequired) {
				redraw();
			}
		}
	}
	
	/**
	 * A list of the elements in position order - please note, calling this method repeatedly is not performant, use a combination
	 * of getRowCount and getElementAtPosition instead.
	 */
	public List<T> getElements() {
		checkWidget();
		return gridModel.getElements();
	}
	
	public int getRowIndex(final T element) {
		checkWidget();
		final Row<T> row = gridModel.getRow(element);
		if (row != null) {
			return row.getRowIndex();
		}
		return -1;
	}

	public void clearElements() {
		checkWidget();
		gridModel.clearElements();
	}
	
	public T getElementAtPosition(final int rowIndex) {
		checkWidget();
		return gridModel.getRows().get(rowIndex).getElement();
	}
	
	public void sort(final Column column, final boolean toggle, final boolean append) {
		checkWidget();
		gridModel.getSortModel().sort(column, toggle, append, true);
	}
	
	public void sort() {
		checkWidget();
		gridModel.getSortModel().refresh();
	}
	
	public void clearSorts() {
		checkWidget();
		gridModel.getSortModel().clear();
	}
	
	public void clearFilters() {
		checkWidget();
		gridModel.getFilterModel().clear();
	}

	public void selectAll() {
		checkWidget();
		gridModel.getSelectionModel().selectAll();
	}
	
	public Collection<T> getSelection() {
		checkWidget();
		return gridModel.getSelectionModel().getSelectedElements();
	}
	
	public void setSelection(final Collection<T> selection) {
		checkWidget();
		gridModel.getSelectionModel().setSelectedElements(selection);
	}

	public Column getAnchorColumn() {
		checkWidget();
		return gridModel.getSelectionModel().getAnchorColumn();
	}
	
	public void setAnchorColumn(final Column column) {
		checkWidget();
		gridModel.getSelectionModel().setAnchorColumn(column);
	}
	
	public T getAnchorElement() {
		checkWidget();
		return gridModel.getSelectionModel().getAnchorElement();
	}
	
	public Row<T> getRow(final T element) {
		checkWidget();
		return gridModel.getRow(element);
	}

	public List<Row<T>> getRows() {
		checkWidget();
		return gridModel.getRows();
	}
	
	public Collection<Row<T>> getAllRows() {
		checkWidget();
		return gridModel.getAllRows();
	}
	
	public List<Row<T>> getColumnHeaderRows() {
		checkWidget();
		return gridModel.getColumnHeaderRows();
	}
	
	public int getDetailedRowCount(final boolean visible, final RowCountScope scope) {
		checkWidget();
		return gridModel.getDetailedRowCount(visible, scope);
	}
	
	public int getRowHeight(final Row<T> row) {
		checkWidget();
		return gridModel.getRowHeight(row);
	}

	public void applyFilters() {
		checkWidget();
		gridModel.fireFiltersChangingEvent();
		gridModel.getFilterModel().applyFilters();
		gridModel.fireFiltersChangedEvent();
	}

	public Collection<Filter<T>> getFilters() {
		checkWidget();
		return gridModel.getFilterModel().getFilters();
	}

	public void addFilters(final Collection<Filter<T>> filters) {
		checkWidget();
		gridModel.getFilterModel().addFilters(filters);
	}

	public void removeFilters(final Collection<Filter<T>> filters) {
		checkWidget();
		gridModel.getFilterModel().removeFilters(filters);
	}
	
	public void setFilters(final Collection<Filter<T>> filtersToRemove, final Collection<Filter<T>> filtersToAdd) {
		checkWidget();
		gridModel.getFilterModel().setFilters(filtersToRemove, filtersToAdd);
	}

	public GridMouseHandler<T> getMouseHandler() {
		checkWidget();
		return mouseHandler;
	}
	
	public GridKeyboardHandler<T> getKeyboardHandler() {
		checkWidget();
		return keyboardHandler;
	}

	public GridRenderer<T> getGridRenderer() {
		checkWidget();
		return gridRenderer;
	}

	public void setGridRenderer(final GridRenderer<T> gridRenderer) {
		checkWidget();
		removePaintListener(this.gridRenderer);
		this.gridRenderer = gridRenderer;
		addPaintListener(gridRenderer);
	}
	
	/**
	 * Override the standard comparator behvaiour.
	 */
	public void setRowComparator(final Comparator<Row<T>> rowComparator) {
		checkWidget();
		gridModel.getSortModel().setRowComparator(rowComparator);
	}

	/**
	 * Don't mess with this.
	 */
	public Viewport<T> getViewport() {
		checkWidget();
		return viewport;
	}

	public IGridToolTipProvider<T> getToolTipProvider() {
		return toolTipProvider;
	}
	
	public void setToolTipProvider(IGridToolTipProvider<T> toolTipProvider) {
		checkWidget();
		this.toolTipProvider = toolTipProvider;
	}
	
	public IGridLabelProvider<T> getLabelProvider() {
//		checkWidget();
		return labelProvider;
	}

	public void setLabelProvider(final IGridLabelProvider<T> labelProvider) {
		checkWidget();
		this.labelProvider = labelProvider;

		if (this.gridModel != null) {
			this.gridModel.setLabelProvider(labelProvider);
		}
		redraw();
	}
	
	public void setContentProvider(final IGridContentProvider<T> contentProvider) {
		checkWidget();
		this.contentProvider = contentProvider;

		if (this.gridModel != null) {
			this.gridModel.setContentProvider(contentProvider);
		}
	}

	public IGridContentProvider<T> getContentProvider() {
		checkWidget();
		return contentProvider;
	}
	
	public void setRowProvider(final IRowProvider<T> rowProvider) {
		checkWidget();
		this.gridModel.setRowProvider(rowProvider);
	}
	
	public IRowProvider<T> getRowProvider() {
		return this.gridModel.getRowProvider();
	}
	
	public void collapseGroups(final Collection<T> elements) {
		checkWidget();
		
		for (T element : elements) {
			contentProvider.setCollapsed(element, true);
		}
		
		applyFilters();
	}
	
	public void expandGroups(final Collection<T> elements) {
		checkWidget();
		
		for (T element : elements) {
			contentProvider.setCollapsed(element, false);
		}
		
		applyFilters();
	}
	
	public void expandAllGroups() {
		checkWidget();
		
		for (Row<T> row : gridModel.getRows()) {
			if (gridModel.isParentElement(row.getElement())) {
				contentProvider.setCollapsed(row.getElement(), false);
			}
		}
		
		applyFilters();
	}
	
	public void collapseAllGroups() {
		checkWidget();
		
		for (Row<T> row : gridModel.getRows()) {
			if (gridModel.isParentElement(row.getElement())) {
				contentProvider.setCollapsed(row.getElement(), true);
			}
		}
		
		applyFilters();
	}
	
	public Column getTrackedColumn() {
		checkWidget();
		return mouseHandler.getColumn();
	}
	
	public Row<T> getTrackedRow() {
		checkWidget();
		return mouseHandler.getRow();
	}
	
	public Column getTrackedBodyColumn() {
		checkWidget();		
		final Column trackedColumn = mouseHandler.getColumn();		
		return ( (trackedColumn == getRowNumberColumn()) || (trackedColumn == getGroupSelectorColumn())) ? null : trackedColumn;
	}
	
	public Row<T> getTrackedBodyRow() {
		checkWidget();
		final Row<T> trackedRow = mouseHandler.getRow(); 
		return (trackedRow == getColumnHeaderRow()) ? null : trackedRow;
	}
	
	public boolean isCtrlHeld() {
		checkWidget();
		return mouseHandler.isCtrlHeld();
	}
	
	public boolean isShiftHeld() {
		checkWidget();
		return mouseHandler.isShiftHeld();
	}
	
	public boolean isAltHeld() {
		checkWidget();
		return mouseHandler.isAltHeld();
	}
	
	public void moveAnchor(final int direction) {
		checkWidget();
		
		//
		// One of the arrow key / directions.
		//
		if ((direction != SWT.ARROW_UP) && (direction != SWT.ARROW_DOWN) && (direction != SWT.ARROW_LEFT) && (direction != SWT.ARROW_RIGHT)) {
			throw new IllegalArgumentException(String.format("An invalid direction was specified %s", direction));
		}
		
		keyboardHandler.moveAnchor(direction);
	}
	
	public Point getTextExtent(final String text, final GC gc, final FontData fontData) {
		checkWidget();
		return gridRenderer.getTextExtent(text, gc, fontData);
	}
	
	public Rectangle getCellBounds(final Column column, final T element) {
		checkWidget();
		
		//
		// Ensure the viewport isn't invalidated before getting coordinates.
		//
		viewport.calculateVisibleCellRange(gc);
		
		final Row<T> row = gridModel.getRow(element);
		final boolean isGroupColumn = getGroupByColumns().contains(column);
		
		if ((row != null) && (column != null)) {
			final int columnX = isGroupColumn ? gridRenderer.getGroupColumnX(gc, column, row) : viewport.getColumnViewportX(gc, column);
			final int rowY = viewport.getRowViewportY(gc, row);			
			final int rowHeight = getRowHeight(row);
			
			if (columnX != -1 && rowY != -1) {
				return new Rectangle(columnX, rowY, viewport.getColumnWidth(columnX, column), rowHeight);	
			}
		}		
		
		return null;
	}
	
	public Rectangle getHeaderBounds(final Column column) {
		checkWidget();
		
		final boolean isGroupColumn = getGroupByColumns().contains(column);
		
		if (isGroupColumn) {
			System.out.println("getHeaderBounds not currently support for group columns");
			return null;
		}
		
		viewport.calculateVisibleCellRange(gc);
		final int x = viewport.getColumnViewportX(gc, column);
		if (x == -1) {
			return null;
		}
		
		return new Rectangle(x, 0, viewport.getColumnWidth(x, column), getRowHeight(gridModel.getColumnHeaderRow()));
	}
	
	/**
	 * Gets the row at the control location, can include the column header row.
	 */
	public Row<T> getRowAtXY(final int x, final int y) {
		checkWidget();
		
		Row<T> row = null;
		
		final int rowIndex = viewport.getRowIndexByY(y, gc);
		if (rowIndex == -1) {
			//
			// See if it's the column header or filter row.
			//
			if (y >= 0 ) {
				row = null;
				final int headerHeight = getRowHeight(gridModel.getColumnHeaderRow());

				if (y < headerHeight) {
					row = gridModel.getColumnHeaderRow();
				}
			}

		} else {
			row = gridModel.getRows().get(rowIndex);			
		}
		
		return row;
	}
	
	/**
	 * Gets the column at the control location, can include the row numbers column or the group selector.
	 */
	public Column getColumnAtXY(final int x, final int y) {
		checkWidget();
		
		Column column = null;
		
		final int columnIndex = viewport.getColumnIndexByX(x, gc);
		
		if ((columnIndex == -1) && (x < viewport.getViewportArea(gc).x)) {
			if (x < gridModel.getRowNumberColumn().getWidth() && gridModel.isShowRowNumbers()) {			
				column = gridModel.getRowNumberColumn();
				
			} else if (gridModel.isShowGroupSelector()) {
				column = gridModel.getGroupSelectorColumn();
			}
			
		} else if (columnIndex != -1) {
			column = gridModel.getColumns().get(columnIndex);
		}
			
		return column;
	}
	
	/**
	 * Scrolls the grid up or down during a drag-drop operation, if the coordinates of the thing being dragged should trigger it.
	 */
	public boolean scrollRowIfNeeded(final int x, final int y) {
		checkWidget();
		
		int vDelta = 0;
		final Row<T> row = getRowAtXY(x, y);
		final int rowIndex = gridModel.getRows().indexOf(row);
				
		if ((rowIndex > 0) && (rowIndex <= viewport.getFirstRowIndex())) {
			//
			// Do we need to scroll up?
			//
			vDelta = -1;
			
		} else if ((rowIndex < gridModel.getRows().size()-1) && (rowIndex >= viewport.getLastRowIndex())) {
			//
			// Do we need to scroll down?
			//
			vDelta = 1;
		}
		
		//System.out.println(String.format("scrollIfNeeded: firstRowIndex [%s] lastRowIndex [%s] hoveredRowIndex [%s] vDelta [%s]", viewport.getFirstRowIndex(), viewport.getLastRowIndex(), gridModel.getRows().indexOf(row), vDelta));
		
		final int VERTICAL_SCROLL = vDelta;
		
		if (vDelta != 0) {
			//
			// Have the scrollbar moved.
			//
			getDisplay().timerExec(100, new Runnable() {					
				@Override
				public void run() {
					getVerticalBar().setSelection(Math.max(getVerticalBar().getMinimum(), getVerticalBar().getSelection() + VERTICAL_SCROLL));				
					gridModel.fireChangeEvent();
				}
			});
		}
				
		return (vDelta != 0);
	}
	
	/**
	 * Ensure the column is just wide enough to fit it's column caption (and sort/filter indicator) and it's
	 * widest data content.
	 */
	public void autoSizeColumn(final Column column) {
		checkWidget();
		
		final int width = gridRenderer.getMinimumWidth(gc, column);
		column.setWidth(width);
		
		//
		// Cause the grid to repaint.
		//
		gridModel.fireChangeEvent();
		gridModel.fireColumnResizedEvent(column);
	}

	public void updateScrollbars() {
		viewport.invalidate();
		viewport.calculateVisibleCellRange(gc);
		
		updateScrollbar(getVerticalBar(), viewport.getHeightInRows(), getRows().size(), viewport.getRowCountLastPage(gc), viewport.getFirstRowIndex(), viewport.getLastRowIndex(), false);		
		updateScrollbar(getHorizontalBar(), viewport.getWidthInColumns(), getColumns().size(), viewport.getColumnCountLastPage(gc), viewport.getFirstColumnIndex(), viewport.getLastColumnIndex() + 1, viewport.isLastColumnCropped());
	}

	/**
	 * Setup the scrollbar based on the item counts specified. Note: This doesn't change the current selected position of the
	 * scrollbar.
	 * 
	 * @param scrollBar
	 *            the scrollbar to configure.
	 * @param viewportSize
	 *            how many columns or rows are currently visible in the
	 *            viewport.
	 * @param maximumSize
	 *            the maximum number of columns or rows.
	 * @param lastPageSize
	 *            the maximum number of columns or rows that currently fits onto
	 *            the last page of the viewport. For rows, this is at the bottom
	 *            of the grid, for columns this is over on the right).
	 */
	private void updateScrollbar(final ScrollBar scrollBar, final int viewportSize, final int maximumSize, final int lastPageSize, final int firstVisibleIndex, final int lastVisibleIndex, final boolean isLastCropped) {
		//
		// The scrollbar doesn't quite hold the total number of items. Because we want the last item to live at the bottom 
		// of the grid (for rows) or the right edge of the grid (for columns) rather than the top/left, we stop scrolling 
		// when the last item is in position. 
		//
		final int cappedMax = maximumSize - (lastPageSize - 1);
		
		//
		// The scrollbar is only required, if there are items beyond either of the viewport edged.
		//
		final boolean visible = ((firstVisibleIndex > 0) 
				|| (((lastVisibleIndex < maximumSize) || (isLastCropped)) && (maximumSize > 1)));
		
		scrollBar.setMaximum(cappedMax);
		scrollBar.setThumb(1);
		scrollBar.setPageIncrement(Math.min(viewportSize, cappedMax));
		scrollBar.setIncrement(1);
		scrollBar.setVisible(visible);
		scrollBar.setEnabled(visible);
		
//		System.out.println(String.format("[%s] -> Scroll page [%s] visible-rows [%s] maximum [%s/%s] last-page [%s] last-vis-idx [%s] last-cropped [%s] visible [%s]", 
//			getData("org.eclipse.swtbot.widget.key"),
//			scrollBar.getPageIncrement(),
//			viewportSize, 
//			maximumSize,
//			scrollBar.getMaximum(),
//			lastPageSize,
//			lastVisibleIndex,
//			isLastCropped,
//			scrollBar.isVisible()));
	}

	private void invalidateComputedArea() {
		computedArea.x = -1;
		computedArea.y = -1;
	}

	public Point getComputedArea() {
		if (computedArea.x == -1 || computedArea.y == -1) {
			computedArea.x = 0;
			computedArea.y = 0;

			for (final Column column : gridModel.getColumns()) {
				computedArea.x += column.getWidth();
			}

			for (final Row<T> row : gridModel.getRows()) {
				computedArea.y += getRowHeight(row);
			}
		}

		return computedArea;
	}
	
	@Override
	public boolean isFocusControl() {
		
		// If we are outright the focus control just return true
		final boolean isFocusControl = super.isFocusControl();
		if( isFocusControl ) {
			return true;
		}
		
		// If we're not the focus control, check to see if one of our 
		// child controls is. This is to handle inline editors etc.
		final Control control = getDisplay().getFocusControl();
		if( isChild(control, getChildren()) ) {
			return true;
		}
		
		return isFocusControl;
	}
	
	/**
	 * Recursively checks to see if the specified child lives somewhere under
	 * this grid
	 * 
	 * @param childToFind
	 * @param children
	 * @return
	 */
	private boolean isChild(Control childToFind, Control[] children) {
		for( Control child : children ) {
			if( child == childToFind ) {
				return true;
			}
			if( child instanceof Composite ) {
				return isChild(childToFind, ((Composite)child).getChildren());
			}
		}
		return false;
	}

	public String getEmptyMessage() {
		checkWidget();
		return emptyMessage;
	}

	public void setEmptyMessage(final String emptyMessage) {
		checkWidget();
		this.emptyMessage = emptyMessage;
		redraw();
	}
	
	public String getEmptyFilterMessage() {
		checkWidget();
		return emptyFilterMessage;
	}

	public void setEmptyFilterMessage(final String emptyFilterMessage) {
		checkWidget();
		this.emptyFilterMessage = emptyFilterMessage;
		redraw();
	}

	public boolean isShowGroupSelector() {
		checkWidget();
		return gridModel.isShowGroupSelector();
	}
	
	public void setShowGroupSelector(final boolean show) {
		checkWidget();
		gridModel.setShowGroupSelector(show);
	}
	
	public boolean isShowRowNumbers() {
		checkWidget();
		return gridModel.isShowRowNumbers();
	}
	
	public void setShowRowNumbers(final boolean show) {
		checkWidget();
		gridModel.setShowRowNumbers(show);
	}

	public boolean isHideNoneHighlightedRows() {
		checkWidget();
		return gridModel.getFilterModel().isHideNoneHighlightedRows();
	}

	public void setHideNoneHighlightedRows(final boolean hideNoneHighlightedRows) {
		checkWidget();
		gridModel.getFilterModel().setHideNoneHighlightedRows(hideNoneHighlightedRows);
	}

	public void addListener(final IGridListener<T> listener) {
		checkWidget();
		this.listeners.add(listener);
	}

	public void removeListener(final IGridListener<T> listener) {
		checkWidget();
		this.listeners.remove(listener);
	}

	public void reveal(final T element) {
		checkWidget();
		final Column column = gridModel.getColumns().get(0);
		final Row<T> row = gridModel.getRow(element);
		
		if (column != null && row != null) {
			viewport.reveal(gc, column, row);		
			fireRevealListeners(column, element);
		}
	}
	
	public void reveal(final Column column, final T element) {
		checkWidget();
		final Row<T> row = gridModel.getRow(element);
		
		if (column != null && row != null) {
			viewport.reveal(gc, column, row);		
			fireRevealListeners(column, element);
		}
	}
	
	protected void fireRevealListeners(Column column, T element) {
		for( IGridListener<T> listener : listeners ) {
			listener.cellRevealed(column, element);
		}
	}

	public void invalidateRowHeights() {
		checkWidget();
		
		for (Row<T> row : gridModel.getAllRows()) {
			row.setHeight(-1);
		}
		
		invalidateComputedArea();
		updateScrollbars();
	}

	private class ScrollListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			viewport.invalidate();
			redraw();
		}
	}

	private class ResizeListener implements Listener {
		private boolean updating = false;
		
		@Override
		public void handleEvent(final Event event) {
			if (event.type == SWT.Resize && !updating) {
				try {
					updating = true;
					updateScrollbars();
				} finally {
					updating = false;
				}
			}
		}
	}

	private class GridDisposeListener implements DisposeListener {
		@Override
		public void widgetDisposed(final DisposeEvent e) {
			dispose();
		}
	}

	private class GridFocusListener extends FocusAdapter {
		@Override
		public void focusLost(final FocusEvent e) {
			//
			// Hide the anchor and any highlighting.
			//
			redraw();
		}
	}
	
	private class GridModelListener implements GridModel.IModelListener<T> {
		/**
		 * A structural or data change that requires a full invalidate then redraw.
		 */		
		@Override
		public void modelChanged() {
			if (isEventsSuppressed()) {
				return;
			}
			
			invalidateComputedArea();
			updateScrollbars();
			redraw();
		}

		@Override
		public void selectionChanged() {
			redraw();

			for (final IGridListener<T> listener : listeners) {
				listener.selectionChanged(gridModel.getSelectionModel().getSelectedElements());
			}
		}
		
		@Override
		public void heightChanged(int delta) {
		}
		
		@Override
		public void rowCountChanged() {
			if (isEventsSuppressed()) {
				return;
			}
			
			updateScrollbars();
			
			for (final IGridListener<T> listener : listeners) {
				listener.rowCountChanged();
			}
		}

		@Override
		public void elementsAdded(Collection<T> elements) {
			for (final IGridListener<T> listener : listeners) {
				listener.elementsAdded(elements);
			}
		}

		@Override
		public void elementsUpdated(Collection<T> elements) {
			for (final IGridListener<T> listener : listeners) {
				listener.elementsUpdated(elements);
			}
		}

		@Override
		public void elementsRemoved(Collection<T> elements) {
			for (final IGridListener<T> listener : listeners) {
				listener.elementsRemoved(elements);
			}
		}
		
		@Override
		public void filtersChanging() {
			for (final IGridListener<T> listener : listeners) {
				listener.filtersChanging();
			}			
		}
		
		@Override
		public void filtersChanged() {
			for (final IGridListener<T> listener : listeners) {
				listener.filtersChanged();
			}			
		}
		
		@Override
		public void columnMoved(Column column) {
			if (isEventsSuppressed()) {
				return;
			}
			
			for (final IGridListener<T> listener : listeners) {
				listener.columnMoved(column);
			}			
		}
		
		@Override
		public void columnResized(Column column) {
			if (isEventsSuppressed()) {
				return;
			}
			
			for (final IGridListener<T> listener : listeners) {
				listener.columnResized(column);
			}			
		}
		
		@Override
		public void columnAboutToSort(Column column) {
			if (isEventsSuppressed()) {
				return;
			}
			
			for (final IGridListener<T> listener : listeners) {
				listener.columnAboutToSort(column);
			}
		}
		
		@Override
		public void columnSorted(Column column) {
			if (isEventsSuppressed()) {
				return;
			}
			
			for (final IGridListener<T> listener : listeners) {
				listener.columnSorted(column);
			}			
		}
		
		@Override
		public void rowNumbersVisibilityChanged(boolean visible) {
			if (isEventsSuppressed()) {
				return;
			}
			
			for (final IGridListener<T> listener : listeners) {
				listener.rowNumbersVisibilityChanged(visible);
			}
		}
		
		@Override
		public void groupSelectorVisibilityChanged(boolean visible) {
			if (isEventsSuppressed()) {
				return;
			}
			
			for (final IGridListener<T> listener : listeners) {
				listener.groupSelectorVisibilityChanged(visible);
			}
		}
	}
}
