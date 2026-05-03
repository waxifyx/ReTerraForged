package raccoonman.reterraforged.client.gui.screen.presetconfig;

import java.awt.Color;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.client.data.RTFTranslationKeys;
import raccoonman.reterraforged.client.gui.Tooltips;
import raccoonman.reterraforged.client.gui.screen.page.BisectedPage;
import raccoonman.reterraforged.client.gui.screen.presetconfig.PresetListPage.PresetEntry;
import raccoonman.reterraforged.client.gui.widget.Slider;
import raccoonman.reterraforged.client.gui.widget.WidgetList;
import raccoonman.reterraforged.concurrent.cache.CacheManager;
import raccoonman.reterraforged.config.PerformanceConfig;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;
import raccoonman.reterraforged.data.worldgen.preset.settings.SpawnType;
import raccoonman.reterraforged.data.worldgen.preset.settings.WorldSettings;
import raccoonman.reterraforged.registries.RTFRegistries;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Levels;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.noise.NoiseUtil;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;
import raccoonman.reterraforged.world.worldgen.util.PosUtil;

public abstract class PresetEditorPage extends BisectedPage<PresetConfigScreen, AbstractWidget, AbstractWidget> {
	private Slider zoom;
	private CycleButton<RenderMode> renderMode;
	private SeedButton seed;
	private Preview preview;
	protected PresetEntry preset;
	
	public PresetEditorPage(PresetConfigScreen screen, PresetEntry preset) {
		super(screen);
		
		this.preset = preset;
	}
	
	protected void regenerate() {
		this.preview.regenerate();
	}
	
	@Override
	public void init() {
		super.init();

		if(this.preview != null) {
			try {
				this.preview.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		this.zoom = PresetWidgets.createIntSlider(Optional.ofNullable(this.zoom).map(Slider::getLerpedValue).orElse(68.0D).intValue(), 1, 100, RTFTranslationKeys.GUI_SLIDER_ZOOM, (slider, value) -> {
			this.regenerate();
			return value;
		});
		RenderMode selectedRenderMode = this.renderMode != null ? this.renderMode.getValue() : RenderMode.BIOME_TYPE;
		if (selectedRenderMode == null) {
			selectedRenderMode = RenderMode.BIOME_TYPE;
		}
		this.renderMode = PresetWidgets.createCycle(ImmutableList.copyOf(RenderMode.values()), selectedRenderMode, Optional.empty(), (button, value) -> {
			this.regenerate();
		}, RenderMode::name);
		this.seed = new SeedButton(this.screen.getSettings().options().seed());

		this.preview = new Preview();
		this.preview.regenerate();

		this.right.addWidget(this.zoom);
		this.right.addWidget(this.renderMode);
		this.right.addWidget(this.seed);
		this.right.addWidget(this.preview);
	}
	
	@Override
	public void onClose() {
		super.onClose();
	
		try {
			this.preset.save();
			this.preview.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onDone() {
		super.onDone();
		
		try {
			this.screen.applyPreset(this.preset);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private class SeedButton extends Button implements WidgetList.ClickOffClose {
		private static final int WHITE = 14737632;
		private static final int RED = 0xFFFF3F30;
		private final EditBox input;
		private long value;
		private boolean editing;

		private SeedButton(long initial) {
			super(-1, -1, -1, -1, CommonComponents.EMPTY, (button) -> {}, Supplier::get);
			this.input = PresetWidgets.createEditBox(PresetEditorPage.this.screen.font, this::onInputChanged, Component.empty());
			this.setTooltip(Tooltips.create(Tooltips.translationKey(RTFTranslationKeys.GUI_BUTTON_SEED)));
			this.syncValue(initial);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (!this.active || !this.visible || !this.isMouseOver(mouseX, mouseY)) {
				if (this.editing && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
					this.finishEditing(true);
				}
				return false;
			}
			if (this.editing) {
				this.syncInputBounds();
				return this.input.mouseClicked(mouseX, mouseY, button);
			}
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				this.beginEditing();
				return true;
			}
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				this.playDownSound(Minecraft.getInstance().getSoundManager());
				this.applySeed(ThreadLocalRandom.current().nextLong());
				return true;
			}
			return false;
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (!this.editing) {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				this.finishEditing(true);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				this.finishEditing(false);
				return true;
			}
			return this.input.keyPressed(keyCode, scanCode, modifiers);
		}

		@Override
		public boolean charTyped(char codePoint, int modifiers) {
			return this.editing && this.input.charTyped(codePoint, modifiers);
		}

		@Override
		public void setFocused(boolean focused) {
			boolean wasFocused = this.isFocused();
			super.setFocused(focused);
			if (wasFocused && !focused && this.editing) {
				this.finishEditing(true);
			}
		}

		@Override
		public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
			if (!this.editing) {
				super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
				return;
			}
			this.syncInputBounds();
			this.input.render(guiGraphics, mouseX, mouseY, partialTicks);
		}

		@Override
		public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
			this.defaultButtonNarrationText(narrationElementOutput);
		}

		private void beginEditing() {
			this.editing = true;
			this.input.setValue(Long.toString(this.value));
			this.onInputChanged(this.input.getValue());
			this.syncInputBounds();
			this.setFocused(true);
			this.input.setFocused(true);
		}

		private void finishEditing(boolean apply) {
			if (apply) {
				this.parseSeed(this.input.getValue()).ifPresent(this::applySeed);
			}
			this.editing = false;
			this.input.setFocused(false);
			super.setFocused(false);
		}

		private void applySeed(long value) {
			this.syncValue(value);
			PresetEditorPage.this.screen.setSeed(value);
			PresetEditorPage.this.regenerate();
		}

		private void syncValue(long value) {
			this.value = value;
			this.setMessage(CommonComponents.optionNameValue(Component.translatable(RTFTranslationKeys.GUI_BUTTON_SEED), Component.literal(Long.toString(value))));
			this.input.setValue(Long.toString(value));
			this.input.setTextColor(WHITE);
		}

		private void syncInputBounds() {
			this.input.setX(this.getX());
			this.input.setY(this.getY());
			this.input.setWidth(this.getWidth());
			this.input.setHeight(this.getHeight());
		}

		private void onInputChanged(String text) {
			this.input.setTextColor(this.parseSeed(text).isPresent() ? WHITE : RED);
		}

		private Optional<Long> parseSeed(String text) {
			if (text == null || text.isBlank()) {
				return Optional.empty();
			}
			try {
				return Optional.of(Long.parseLong(text.trim()));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		}
	}
	
	public class Preview extends Button {
	    private static final int FACTOR = 4;
	    public static final int SIZE = (1 << 4) << FACTOR;
	    private static final float[] LEGEND_SCALES = { 1, 0.9F, 0.75F, 0.6F };
	    private DynamicTexture texture = new DynamicTexture(new NativeImage(SIZE, SIZE, false));
	    private ResourceLocation textureId = Minecraft.getInstance().getTextureManager().register(RTFCommon.MOD_ID + "-preview-framebuffer", this.texture); 
	    private Tile tile;
	    private int centerX, centerZ;
	    
	    private String hoveredCoords = "";
	    //TODO maybe make this a map or something instead?
	    private String[] legendValues = {"", "", ""};
	    private Component[] legendLabels = { Component.translatable(RTFTranslationKeys.GUI_LABEL_PREVIEW_AREA), Component.translatable(RTFTranslationKeys.GUI_LABEL_PREVIEW_TERRAIN), Component.translatable(RTFTranslationKeys.GUI_LABEL_PREVIEW_BIOME) };
	    
	    private int offsetX, offsetZ;

	    public Preview() {
	        super(-1, -1, -1, -1, CommonComponents.EMPTY, (b) -> {
		    	System.out.println("clicked");
	        	Minecraft mc = Minecraft.getInstance();
	        	MouseHandler mouse = mc.mouseHandler;
	        	if(b instanceof Preview self) {
			        if (self.updateLegend((int) mouse.xpos(), (int) mouse.ypos()) && !self.hoveredCoords.isEmpty()) {
			            self.playDownSound(Minecraft.getInstance().getSoundManager());
			            PresetEditorPage.this.screen.minecraft.keyboardHandler.setClipboard(self.hoveredCoords);
			        }
	        	}
	        }, DEFAULT_NARRATION);
	    }

	    public void regenerate() {
			WorldCreationContext settings = PresetEditorPage.this.screen.getSettings();
	        RegistryAccess.Frozen registries = settings.worldgenLoadContext();
	        HolderLookup.Provider provider = PresetEditorPage.this.preset.getPreset().buildPatch(registries);
	        HolderGetter<Preset> presets = provider.lookupOrThrow(RTFRegistries.PRESET);
	        HolderGetter<Noise> noises = provider.lookupOrThrow(RTFRegistries.NOISE);
	        Preset preset = presets.getOrThrow(Preset.KEY).value();
	        WorldSettings world = preset.world();
	        WorldSettings.Properties properties = world.properties;
	        
	        try {
				CacheManager.clear();
			} catch (Exception e) {
				e.printStackTrace();
			}
			PerformanceConfig config = PerformanceConfig.read(PerformanceConfig.DEFAULT_FILE_PATH)
				.resultOrPartial(RTFCommon.LOGGER::error)
				.orElseGet(PerformanceConfig::makeDefault);
	        GeneratorContext generatorContext = GeneratorContext.makeUncached(preset, noises, (int) settings.options().seed(), FACTOR, 0, config.batchCount());
	        
	        this.centerX = 0;
	        this.centerZ = 0;
	        
	        if(preset.world().properties.spawnType == SpawnType.CONTINENT_CENTER) {
	        	long nearestContinentCenter = generatorContext.lookup.getHeightmap().continent().getNearestCenter(this.offsetX, this.offsetZ);
	        	this.centerX = PosUtil.unpackLeft(nearestContinentCenter);
	        	this.centerZ = PosUtil.unpackRight(nearestContinentCenter);
	        } else {
	        	this.centerX = 0;
	        	this.centerZ = 0;
	        }

	        this.tile = generatorContext.generator.generateZoomed(this.centerX, this.centerZ, this.getZoom(), false).join();
	        RenderMode renderMode = PresetEditorPage.this.renderMode.getValue();
	        Levels levels = new Levels(properties.terrainScaler(), properties.seaLevel);

	        int stroke = 2;
	        int width = this.tile.getBlockSize().size();

	        NativeImage pixels = this.texture.getPixels();
	        this.tile.iterate((cell, x, z) -> {
	            if (x < stroke || z < stroke || x >= width - stroke || z >= width - stroke) {
	                pixels.setPixelRGBA(x, z, Color.BLACK.getRGB());
	            } else {
	                pixels.setPixelRGBA(x, z, renderMode.getColor(cell, levels));
	            }
	        });
	        this.texture.upload();
	    }
	    
	    public void close() throws Exception {
	    	this.texture.close();
	    	try {
				CacheManager.clear();
			} catch (Exception e) {
				e.printStackTrace();
			}
	    }

	    @Override
	    public void renderWidget(GuiGraphics guiGraphics, int mx, int my, float partialTicks) {
	    	int x = this.getX();
	    	int y = this.getY();
	    	
	    	this.height = this.getWidth();
	        RenderSystem.enableBlend();
	        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
	        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
	    	guiGraphics.blit(this.textureId, x, y, 0, 0, this.width, this.height, this.width, this.height);

	    	this.updateLegend(mx, my);

	    	this.renderLegend(guiGraphics, mx, my, this.legendLabels, this.legendValues, x, y + this.width, 10, 0xFFFFFF);
	    }

	    private boolean updateLegend(int mx, int my) {
	        if (this.tile != null) {
	            int left = this.getX();
	            int top = this.getY();
	            float size = this.width;
	
	            int zoom = this.getZoom();
	            int width = Math.max(1, this.tile.getBlockSize().size() * zoom);
	            int height = Math.max(1, this.tile.getBlockSize().size() * zoom);
	            this.legendValues[0] = width + "x" + height;
	            if (mx >= left && mx <= left + size && my >= top && my <= top + size) {
	                float fx = (mx - left) / size;
	                float fz = (my - top) / size;
	                int ix = NoiseUtil.round(fx * this.tile.getBlockSize().size());
	                int iz = NoiseUtil.round(fz * this.tile.getBlockSize().size());
	                Cell cell = this.tile.lookup(ix, iz);
	                this.legendValues[1] = getTerrainName(cell);
	                this.legendValues[2] = getBiomeName(cell);
	
	                int dx = (ix - (this.tile.getBlockSize().size() / 2)) * zoom;
	                int dz = (iz - (this.tile.getBlockSize().size() / 2)) * zoom;
	
	                this.hoveredCoords = (this.centerX + dx) + ":" + (this.centerZ + dz);
	                return true;
	            } else {
	            	this.hoveredCoords = "";
	            }
	        }
	        return false;
	    }

	    private float getLegendScale() {
	        int index = PresetEditorPage.this.screen.minecraft.options.guiScale().get() - 1;
	        if (index < 0 || index >= LEGEND_SCALES.length) {
	            // index=-1 == GuiScale(AUTO) which is the same as GuiScale(4)
	            // values above 4 don't exist but who knows what mods might try set it to
	            // in both cases use the smallest acceptable scale
	            index = LEGEND_SCALES.length - 1;
	        }
	        return LEGEND_SCALES[index];
	    }

	    private void renderLegend(GuiGraphics guiGraphics, int mx, int my, Component[] labels, String[] values, int left, int top, int lineHeight, int color) {
	        float scale = this.getLegendScale();
	        PoseStack pose = guiGraphics.pose();
	        	
	        pose.pushPose();
	        pose.translate(left + 3.75F * scale, top - lineHeight * (3.2F * scale), 0);
	        pose.scale(scale, scale, 1);
	
	        Minecraft mc = Minecraft.getInstance();
	        Font renderer = mc.font;
	        int spacing = 0;
	        for (Component s : labels) {
	            spacing = Math.max(spacing, renderer.width(s));
	        }
	
	        float maxWidth = (this.width - 4) / scale;
	        for (int i = 0; i < labels.length && i < values.length; i++) {
	        	Component label = labels[i];
	            String value = values[i];
	
	            while (value.length() > 0 && spacing + renderer.width(value) > maxWidth) {
	                value = value.substring(0, value.length() - 1);
	            }
	
	            guiGraphics.drawString(renderer, label, 0, i * lineHeight, color);
	            guiGraphics.drawString(renderer, value, spacing, i * lineHeight, color);
	        }
	
	        pose.popPose();
	
	        if (!this.hoveredCoords.isEmpty()) {
	        	guiGraphics.drawCenteredString(renderer, this.hoveredCoords, mx, my - 10, 0xFFFFFF);
	        }
	    }
	
	    private int getZoom() {
	        return NoiseUtil.round(1.5F * (101 - (float) PresetEditorPage.this.zoom.getLerpedValue()));
	    }
	
	    private static String getTerrainName(Cell cell) {
	        if (cell.terrain.isRiver()) {
	            return "river";
	        }
	        return cell.terrain.getName().toLowerCase();
	    }
	
	    private static String getBiomeName(Cell cell) {
	        String terrain = cell.terrain.getName().toLowerCase();
	        if (terrain.contains("ocean")) {
	            if (cell.temperature < 0.3F) {
	                return "cold_" + terrain;
	            }
	            if (cell.temperature > 0.6F) {
	                return "warm_" + terrain;
	            }
	            return terrain;
	        }
	        if (terrain.contains("river")) {
	            return "river";
	        }
	        return cell.biome.name().toLowerCase();
	    }
	}
}
