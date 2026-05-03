package raccoonman.reterraforged.client.gui.widget;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
public class WidgetList<T extends AbstractWidget> extends ContainerObjectSelectionList<WidgetList.Entry<T>> {
	private boolean renderSelected;
	private Entry<T> draggedEntry;
	
    public WidgetList(Minecraft minecraft, int i, int j, int k, int l) {
        super(minecraft, i, j, k, l);
    }

    public void select(T widget) {
    	for(Entry<T> entry : this.children()) {
    		if(entry.widget.equals(widget)) {
    			this.setSelected(entry);
    			return;
    		}
    	}
    }
    
    public <W extends T> W addWidget(W widget) {
        super.addEntry(new Entry<>(widget));
        return widget;
    }

    public void setRenderSelected(boolean renderSelected) {
    	this.renderSelected = renderSelected;
    }

	public T getFocusedWidget() {
		Entry<T> focused = this.getFocused();
		return focused == null ? null : focused.getWidget();
	}

	public void clearFocusedWidget() {
		Entry<T> focused = this.getFocused();
		this.draggedEntry = null;
		if (focused != null) {
			focused.setFocused(null);
			this.setFocused(null);
		}
	}

	public interface ClickOffClose {
	}

	public int getRowTop(int index) {
		return super.getRowTop(index);
	}

    @Override
    protected boolean isSelectedItem(int i) {
        return this.renderSelected && Objects.equals(this.getSelected(), this.children().get(i));
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

	@Override
	protected int getScrollbarPosition() {
		return this.getRowRight();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Entry<T> clickedEntry = this.getEntryAtPosition(mouseX, mouseY);
		Entry<T> focusedEntry = this.getFocused();
		if (focusedEntry != null && focusedEntry != clickedEntry) {
			focusedEntry.setFocused(null);
		}
		if (super.mouseClicked(mouseX, mouseY, button)) {
			this.draggedEntry = clickedEntry;
			return true;
		}
		Entry<T> entry = clickedEntry;
		if (entry != null && entry.getWidget().mouseClicked(mouseX, mouseY, button)) {
			this.draggedEntry = entry;
			this.setFocused(entry);
			entry.setFocused(entry.getWidget());
			return true;
		}
		this.draggedEntry = null;
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		Entry<T> entry = this.draggedEntry;
		if (entry != null && entry.getWidget().mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		Entry<T> entry = this.draggedEntry;
		this.draggedEntry = null;
		if (entry != null && entry.getWidget().mouseReleased(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	public static class Entry<T extends AbstractWidget> extends ContainerObjectSelectionList.Entry<Entry<T>> {
        private T widget;

        public Entry(T widget) {
            this.widget = widget;
        }

        public T getWidget() {
        	return this.widget;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.singletonList(this.widget);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            int optionWidth = Math.min(396, width);
            int padding = (width - optionWidth) / 2;
            widget.setX(left + padding);
            widget.setY(top);
            widget.visible = true;
            widget.setWidth(optionWidth);
            widget.setHeight(height - 1);
            widget.render(guiGraphics, mouseX, mouseY, partialTicks);
        }

		@Override
		public List<T> narratables() {
			return Collections.singletonList(this.widget);
		}
    }
}
