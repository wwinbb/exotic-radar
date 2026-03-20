package com.exoticradar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ClickableRowWidget extends AbstractWidget {

    private final PlayerScanResult result;
    private final DashboardScreen  parent;
    private final int rowIndex;

    private static final int C_ROW_EVEN = 0xFF111111;
    private static final int C_ROW_ODD  = 0xFF171717;
    private static final int C_ROW_HI   = 0xFF1C1C3A;
    private static final int C_ROW_EXO  = 0xFF2A0808;
    private static final int C_WHITE    = 0xFFFFFFFF;
    private static final int CX_NAME    = 6;
    private static final int CX_UUID    = 112;
    private static final int CX_STATUS  = 352;
    private static final int CX_EXOTIC  = 404;
    private static final int CX_REASON  = 448;

    public ClickableRowWidget(int x, int y, int w, int h,
                              PlayerScanResult result, int rowIndex, DashboardScreen parent) {
        super(x, y, w, h, Component.empty());
        this.result   = result;
        this.rowIndex = rowIndex;
        this.parent   = parent;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        boolean hov = isHovered();
        int bg = result.isExotic   ? C_ROW_EXO
               : hov               ? C_ROW_HI
               : rowIndex % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD;
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);
        int ty = getY() + 2;
        var font = Minecraft.getInstance().font;
        g.drawString(font, (result.isExotic ? "§c" : "") + result.username, CX_NAME, ty, C_WHITE);
        g.drawString(font, "§8" + (result.uuid.equals("unknown") ? "—" : result.uuid), CX_UUID, ty, C_WHITE);
        g.drawString(font, switch (result.status) {
            case PENDING  -> "§9PEND";
            case CHECKING -> "§6CHECK";
            case DONE     -> "§aDONE";
            case FAILED   -> "§cFAIL";
        }, CX_STATUS, ty, C_WHITE);
        g.drawString(font, switch (result.status) {
            case DONE    -> result.isExotic ? "§c§lYES" : "§7no";
            case FAILED  -> "§4ERR";
            default      -> "§7—";
        }, CX_EXOTIC, ty, C_WHITE);
        g.drawString(font,
            "§7" + (result.status == PlayerScanResult.Status.FAILED
                ? result.failReason : result.exoticReason),
            CX_REASON, ty, C_WHITE);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean bl) {
        Minecraft.getInstance().setScreen(new PlayerDetailScreen(result, parent));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
