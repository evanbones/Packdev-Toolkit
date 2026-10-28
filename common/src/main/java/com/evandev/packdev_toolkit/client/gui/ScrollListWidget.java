package com.evandev.packdev_toolkit.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A scrolling list drawn in a dark well. Override {@link #getContentWidth()} to also scroll sideways.
 * The widget's bounds include the scrollbars.
 */
public abstract class ScrollListWidget<E extends ScrollListWidget.Entry> extends AbstractWidget {
    private static final int WELL_INSET = 2;

    private final List<E> entries = new ArrayList<>();
    private final int itemHeight;
    private double scrollAmount = 0;
    private double scrollX = 0;
    private boolean draggingScrollbar = false;
    private boolean draggingHorizontalScrollbar = false;

    protected ScrollListWidget(int x, int y, int width, int height, int itemHeight) {
        super(x, y, width, height, CommonComponents.EMPTY);
        this.itemHeight = itemHeight;
    }

    public List<E> children() {
        return this.entries;
    }

    protected void clearEntries() {
        this.entries.clear();
    }

    protected void addEntry(E entry) {
        this.entries.add(entry);
    }

    public int getRowLeft() {
        return this.getX() + WELL_INSET;
    }

    public int getRowWidth() {
        return this.width - GuiUtil.SCROLLBAR_WIDTH - GuiUtil.SCROLLBAR_GAP - WELL_INSET * 2;
    }

    /**
     * Where rows start once scrolled sideways. Rows are drawn from here, so hit tests inside a row should use it too.
     */
    public int getScrolledRowLeft() {
        return getRowLeft() - (int) this.scrollX;
    }

    /**
     * The width the rows need. Anything wider than {@link #getRowWidth()} makes the list scroll sideways.
     */
    protected int getContentWidth() {
        return getRowWidth();
    }

    private int getMaxScrollX() {
        return Math.max(0, getContentWidth() - getRowWidth());
    }

    private boolean hasHorizontalScrollbar() {
        return getMaxScrollX() > 0;
    }

    private int wellWidth() {
        return this.width - GuiUtil.SCROLLBAR_WIDTH - GuiUtil.SCROLLBAR_GAP;
    }

    private int wellHeight() {
        return this.height - (hasHorizontalScrollbar() ? GuiUtil.SCROLLBAR_WIDTH + GuiUtil.SCROLLBAR_GAP : 0);
    }

    private int contentTop() {
        return this.getY() + WELL_INSET;
    }

    private int contentHeight() {
        return wellHeight() - WELL_INSET * 2;
    }

    private int scrollbarX() {
        return this.getX() + this.width - GuiUtil.SCROLLBAR_WIDTH;
    }

    private int horizontalScrollbarY() {
        return this.getY() + this.height - GuiUtil.SCROLLBAR_WIDTH;
    }

    private int getMaxScroll() {
        return Math.max(0, this.entries.size() * this.itemHeight - contentHeight());
    }

    public void clampScroll() {
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0, getMaxScroll());
        this.scrollX = Mth.clamp(this.scrollX, 0, getMaxScrollX());
    }

    public double getScrollAmount() {
        return this.scrollAmount;
    }

    public double getScrollX() {
        return this.scrollX;
    }

    /**
     * Restores a scroll position, e.g. after the widget was rebuilt. Call once the entries are in.
     */
    public void setScroll(double scrollAmount, double scrollX) {
        this.scrollAmount = scrollAmount;
        this.scrollX = scrollX;
        clampScroll();
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= scrollbarX() && mouseX < this.getX() + this.width
                && mouseY >= this.getY() && mouseY < this.getY() + wellHeight();
    }

    private boolean isOverHorizontalScrollbar(double mouseX, double mouseY) {
        return hasHorizontalScrollbar() && mouseX >= this.getX() && mouseX < this.getX() + wellWidth()
                && mouseY >= horizontalScrollbarY() && mouseY < this.getY() + this.height;
    }

    private double scrollXFromMouse(double mouseX) {
        return GuiUtil.scrollAmountFromMouse(mouseX, this.getX(), wellWidth(), getMaxScrollX());
    }

    private int entryIndexAt(double mouseX, double mouseY) {
        if (mouseX < getRowLeft() || mouseX >= getRowLeft() + getRowWidth()
                || mouseY < contentTop() || mouseY >= contentTop() + contentHeight()) {
            return -1;
        }
        int index = (int) Math.floor((mouseY - contentTop() + this.scrollAmount) / this.itemHeight);
        return index >= 0 && index < this.entries.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        // Shift turns the wheel sideways; trackpads can scroll sideways directly
        double sideways = Screen.hasShiftDown() ? scrollY : scrollX;
        double vertical = Screen.hasShiftDown() ? 0 : scrollY;
        boolean scrolled = false;
        if (sideways != 0 && getMaxScrollX() > 0) {
            this.scrollX = Mth.clamp(this.scrollX - sideways * 20, 0, getMaxScrollX());
            scrolled = true;
        }
        if (vertical != 0 && getMaxScroll() > 0) {
            this.scrollAmount = Mth.clamp(this.scrollAmount - vertical * this.itemHeight * 1.5, 0, getMaxScroll());
            scrolled = true;
        }
        return scrolled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.isMouseOver(mouseX, mouseY)) return false;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverScrollbar(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            this.scrollAmount = GuiUtil.scrollAmountFromMouse(mouseY, this.getY(), wellHeight(), getMaxScroll());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverHorizontalScrollbar(mouseX, mouseY)) {
            this.draggingHorizontalScrollbar = true;
            this.scrollX = scrollXFromMouse(mouseX);
            return true;
        }

        int index = entryIndexAt(mouseX, mouseY);
        if (index >= 0) {
            return this.entries.get(index).mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (this.draggingScrollbar) {
            this.scrollAmount = GuiUtil.scrollAmountFromMouse(mouseY, this.getY(), wellHeight(), getMaxScroll());
            return true;
        }
        if (this.draggingHorizontalScrollbar) {
            this.scrollX = scrollXFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScrollbar = false;
        this.draggingHorizontalScrollbar = false;
        return false;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        clampScroll();
        GuiUtil.drawWell(guiGraphics, this.getX(), this.getY(), wellWidth(), wellHeight());

        int hoveredIndex = entryIndexAt(mouseX, mouseY);
        int left = getRowLeft();
        int rowWidth = getRowWidth();
        int scrolledLeft = getScrolledRowLeft();
        int scrolledWidth = Math.max(rowWidth, getContentWidth());

        guiGraphics.enableScissor(left, contentTop(), left + rowWidth, contentTop() + contentHeight());
        for (int i = 0; i < this.entries.size(); i++) {
            int top = contentTop() - (int) this.scrollAmount + i * this.itemHeight;
            if (top + this.itemHeight >= contentTop() && top <= contentTop() + contentHeight()) {
                this.entries.get(i).render(guiGraphics, top, scrolledLeft, scrolledWidth, this.itemHeight, mouseX, mouseY, i == hoveredIndex);
            }
        }
        guiGraphics.disableScissor();

        GuiUtil.drawScrollbar(guiGraphics, scrollbarX(), this.getY(), wellHeight(), this.scrollAmount, getMaxScroll());
        if (hasHorizontalScrollbar()) {
            GuiUtil.drawHorizontalScrollbar(guiGraphics, this.getX(), horizontalScrollbarY(), wellWidth(), this.scrollX, getMaxScrollX());
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
    }

    public interface Entry {
        void render(GuiGraphics guiGraphics, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered);

        boolean mouseClicked(double mouseX, double mouseY, int button);
    }
}
