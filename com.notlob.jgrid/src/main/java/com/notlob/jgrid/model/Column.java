package com.notlob.jgrid.model;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.GC;

import com.notlob.jgrid.Grid;
import com.notlob.jgrid.styles.AlignmentStyle;

@SuppressWarnings("rawtypes")
public class Column {

	private final String columnId;
	private String caption;
	private int width;
	private Comparator comparator;
	private SortDirection sortDirection;
	private int sortSequence;
	private boolean visible;
	private boolean pinned;
	private boolean wrap;
	private boolean selected;
	private AlignmentStyle textAlignment;
	private AlignmentStyle imageAlignment;

	private Grid<?> grid;
	private GC gc;
	
	// Arbitrary things can be tagged onto a column by key.
	private Map<String, Object> dataByKey;

	public Column(final String columnId) {
		this.columnId = columnId;
		this.width = 125;
		this.sortDirection = SortDirection.NONE;
		this.comparator = new DefaultComparator();
		this.visible = true;
		this.pinned = false;
		this.wrap = false;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(String.format("%s ", this.getClass().getSimpleName()));
		sb.append(String.format("[%s]", columnId));
		sb.append(String.format(" Caption [%s]", caption));
		sb.append(String.format(" Width [%s]", width));
		sb.append(String.format(" [%s]", visible ? "visible" : "hidden"));
		sb.append(String.format(" [%s]", pinned ? "pinned" : "not pinned"));
		sb.append(String.format(" [%s]", selected ? "selected" : "not selected"));
		sb.append(String.format(" Sort [%s, %s]", sortDirection, sortSequence));
		sb.append(String.format(" Text-Align [%s] Image-Align [%s]", textAlignment, imageAlignment));
		sb.append(String.format(" Wrap-Text [%s]", wrap));

		return sb.toString();
	}

	/**
	 * The grid is needed to calculate the content width of elastic columns (the last ones).
	 */
	void setGrid(final Grid<?> grid) {
		this.grid = grid;
		this.gc = grid.getGC();
		
		if (grid != null) {
			this.grid.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					if (gc != null) {
						gc.dispose();
						gc = null;
					}
				}
			});
		}
	}
	
	public void setCaption(final String caption) {
		this.caption = caption;
	}

	public String getCaption() {
		return caption;
	}

	public String getColumnId() {
		return columnId;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(final int width) {
		this.width = width;
	}

	public Comparator getComparator() {
		return comparator;
	}

	public void setComparator(final Comparator comparator) {
		this.comparator = comparator;
	}

	public SortDirection getSortDirection() {
		return sortDirection;
	}

	public void setSortDirection(final SortDirection sortDirection) {
		this.sortDirection = sortDirection;
	}

	public int getSortSequence() {
		return sortSequence;
	}

	public void setSortSequence(final int sortSequence) {
		this.sortSequence = sortSequence;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(final boolean visible) {
		this.visible = visible;
	}
	
	public boolean isSelected() {
		return selected;
	}
	
	// Intentionally package protected - let the selection model use it.
	void setSelected(boolean selected) {
		this.selected = selected;
	}
	
	public boolean isPinned() {
		return pinned;
	}
	
	public void setPinned(final boolean pinned) {
		this.pinned = pinned;
	}
	
	public boolean isWrap() {
		return wrap;
	}
	
	public void setWrap(final boolean wrap) {
		this.wrap = wrap;
	}
	
	public AlignmentStyle getTextAlignment() {
		return textAlignment;
	}

	public void setTextAlignment(final AlignmentStyle textAlignment) {
		this.textAlignment = textAlignment;
	}

	public AlignmentStyle getImageAlignment() {
		return imageAlignment;
	}

	public void setImageAlignment(final AlignmentStyle imageAlignment) {
		this.imageAlignment = imageAlignment;
	}
	
	public boolean isLastColumn() {
 		return (grid != null) && (grid.getColumns().get(grid.getColumns().size() - 1) == this);
 	}

	public Object getData(final String key) {
		if (dataByKey != null) {
			return dataByKey.get(key);
		}

		return null;
	}

	public void setData(final String key, final Object data) {
		if (dataByKey == null) {
			dataByKey = new HashMap<String, Object>();
		}

		dataByKey.put(key, data);
	}

	public boolean hasData(final String key) {
		if (dataByKey != null) {
			return dataByKey.containsKey(key);
		}

		return false;
	}

	private class DefaultComparator implements Comparator<Object> {
		@Override
		public int compare(final Object o1, final Object o2) {

			if ((o1 == null) && (o2 == null)) {
				return 0;
			}

			if (o1 == null) {
				return -1;
			}

			if (o2 == null) {
				return 1;
			}
			
			return Collator.getInstance().compare(String.valueOf(o1), String.valueOf(o2));
		}
	}
}
