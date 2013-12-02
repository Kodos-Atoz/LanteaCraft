package pcl.lc;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import pcl.lc.LanteaCraft.Blocks;
import pcl.lc.LanteaCraft.Items;
import pcl.lc.base.TileEntityChunkManager;
import pcl.lc.blocks.BlockNaquadah;
import pcl.lc.blocks.BlockNaquadahOre;
import pcl.lc.blocks.BlockStargateBase;
import pcl.lc.blocks.BlockStargateController;
import pcl.lc.blocks.BlockStargateRing;
import pcl.lc.compat.UpgradeHelper;
import pcl.lc.config.ConfigValue;
import pcl.lc.config.ConfigurationHelper;
import pcl.lc.container.ContainerStargateBase;
import pcl.lc.core.EnumGuiList;
import pcl.lc.core.GateAddressHelper;
import pcl.lc.forge.HelperGUIHandler;
import pcl.lc.generators.ChunkData;
import pcl.lc.generators.FeatureGeneration;
import pcl.lc.generators.FeatureUnderDesertPyramid;
import pcl.lc.generators.NaquadahOreWorldGen;
import pcl.lc.generators.TradeHandler;
import pcl.lc.items.ItemDebugTool;
import pcl.lc.items.ItemStargateRing;
import pcl.lc.items.ItemTokraSpawnEgg;
import pcl.lc.network.ClientPacketHandler;
import pcl.lc.network.NetworkHelpers;
import pcl.lc.network.LanteaPacket;
import pcl.lc.network.ServerPacketHandler;
import pcl.lc.tileentity.TileEntityStargateBase;
import pcl.lc.tileentity.TileEntityStargateController;
import pcl.lc.tileentity.TileEntityStargateRing;
import pcl.lc.util.AnalyticsHelper;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CallableMinecraftVersion;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Property;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.event.world.WorldEvent.Save;
import net.minecraftforge.event.world.WorldEvent.Unload;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.common.registry.VillagerRegistry;
import cpw.mods.fml.common.registry.VillagerRegistry.IVillageTradeHandler;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;

public class LanteaCraftCommonProxy {

	protected File cfgFile;
	protected ConfigurationHelper config;
	protected ArrayList<ConfigValue<?>> configValues = new ArrayList<ConfigValue<?>>();

	// public StargateNetworkChannel channel;
	public TileEntityChunkManager chunkManager;

	private NaquadahOreWorldGen naquadahOreGenerator;
	private int tokraVillagerID;

	private AnalyticsHelper analyticsHelper = new AnalyticsHelper(false, null);

	protected Map<Integer, Class<? extends Container>> registeredContainers = new HashMap<Integer, Class<? extends Container>>();
	protected Map<Integer, Class<? extends GuiScreen>> registeredGUIs = new HashMap<Integer, Class<? extends GuiScreen>>();
	protected Map<String, VSBinding> registeredVillagers = new HashMap<String, VSBinding>();

	protected ClientPacketHandler defaultClientPacketHandler;
	protected ServerPacketHandler defaultServerPacketHandler;
	protected NetworkHelpers networkHelpers;

	public static UpgradeHelper upgradeHelper;

	protected static class IDBinding<T> {
		public int id;
		public T object;
	}

	static class VSBinding extends IDBinding<ResourceLocation> {
	};

	public LanteaCraftCommonProxy() {
		System.out.println("This is " + BuildInfo.modName + " version " + BuildInfo.versionNumber + " build "
				+ BuildInfo.buildNumber + " as modid " + BuildInfo.modID);

		defaultClientPacketHandler = new ClientPacketHandler();
		defaultServerPacketHandler = new ServerPacketHandler();
		networkHelpers = new NetworkHelpers();
	}

	public void preInit(FMLPreInitializationEvent e) {
		cfgFile = e.getSuggestedConfigurationFile();
		config = new ConfigurationHelper(cfgFile);
		configure();
	}

	public void init(FMLInitializationEvent e) {
		LanteaCraft.getLogger().log(Level.INFO, "LanteaCraft setting up...");
		MinecraftForge.EVENT_BUS.register(LanteaCraft.getInstance());
		chunkManager = new TileEntityChunkManager(LanteaCraft.getInstance());
		NetworkRegistry.instance().registerGuiHandler(LanteaCraft.getInstance(), new HelperGUIHandler());
		networkHelpers.init();
	}

	public void postInit(FMLPostInitializationEvent e) {
		registerBlocks();
		registerTileEntities();
		registerItems();
		registerOres();
		registerRecipes();
		registerRandomItems();
		registerWorldGenerators();
		registerContainers();
		registerVillagers();
		registerOther();
		// channel = new StargateNetworkChannel(BuildInfo.modID);
		if (config.extended)
			config.save();
		LanteaCraft.getLogger().log(Level.INFO, "LanteaCraft done setting up!");

		LanteaCraft.getLogger().log(Level.INFO, "[COMPAT] LanteaCraft looking for other versions of SGCraft...");
		if (UpgradeHelper.detectSGCraftInstall() || UpgradeHelper.detectSGCraftReloadedInstall()) {
			upgradeHelper = new UpgradeHelper();
			if (upgradeHelper.detectSGCraftInstall())
				upgradeHelper.hookSGCraft();
			if (upgradeHelper.detectSGCraftReloadedInstall())
				upgradeHelper.hookSGCraftReloaded();
		}
		LanteaCraft.getLogger().log(Level.INFO, "[COMPAT] LanteaCraft done looking for other versions.");
	}

	public void onChunkLoad(ChunkDataEvent.Load e) {
		LanteaCraft.getLogger().log(Level.FINE, "ChunkDataEvent.Load fired");
		ChunkData.onChunkLoad(e);
	}

	public void onChunkSave(ChunkDataEvent.Save e) {
		LanteaCraft.getLogger().log(Level.FINE, "ChunkDataEvent.Save fired");
		ChunkData.onChunkSave(e);
	}

	public void onInitMapGen(InitMapGenEvent e) {
		LanteaCraft.getLogger().log(Level.FINE, "InitMapGenEvent fired");
		FeatureGeneration.onInitMapGen(e);
	}

	void configure() {
		TileEntityStargateController.configure(config);
		NaquadahOreWorldGen.configure(config);
		TileEntityStargateBase.configure(config);
		configValues.add(new ConfigValue<Boolean>("addOresToExistingWorlds", config.getBoolean("options",
				"addOresToExistingWorlds", false)));

		String version = new StringBuilder().append(BuildInfo.versionNumber).append(" build ")
				.append(BuildInfo.buildNumber).toString();
		Property prop = config.get("general", "currentVersion", 0);
		prop.comment = "Version cache - do not change this!";
		String previousVersion = prop.getString();

		prop.set(version);

		Property GenerateStruct = config.get("options", "GenerateStructures", true);
		configValues.add(new ConfigValue<Boolean>("doGenerateStructures", config.getBoolean("stargate",
				"GenerateStructures", true)));
		GenerateStruct.comment = "Enables/Disables generation of Gate Rooms under Desert Pyramids. (true/false)";

		Property GalacticraftCompat = config.get("options", "GalacticCraftCompat", false);
		configValues.add(new ConfigValue<Boolean>("doGalacticCraftCompat", config.getBoolean("stargate",
				"GalacticCraftCompat", true)));
		GalacticraftCompat.comment = "Enables/Disables Galcticraft support - this will change all addresses! (true/false)";

		Property textureRes = config.get("graphics_options", "textureRes", 32);
		configValues.add(new ConfigValue<Integer>("renderQuality", config.getInteger("graphics_options", "textureRes",
				32)));
		textureRes.comment = "Texture resolution setting. (32 / 64 / 128)";

		Property HDModels = config.get("graphics_options", "HDModels", true);
		configValues.add(new ConfigValue<Boolean>("renderUseModels", config.getBoolean("graphics_options", "HDModels",
				true)));
		HDModels.comment = "Should HD models be used. (true/false)";

		configValues.add(new ConfigValue<Boolean>("doGateExplosion", config.getBoolean("options",
				"ActiveGateExplosion", true)));

		Property enableAnalytics = config.get("options", "enableAnalytics", true);
		configValues.add(new ConfigValue<Boolean>("enableAnalytics", config.getBoolean("options", "enableAnalytics",
				true)));
		enableAnalytics.comment = "Submit anonymous usage statistic data. (true/false)";

		if (((ConfigValue<Boolean>) getConfigValue("doGalacticCraftCompat")).getValue())
			GateAddressHelper.minDimension = -99;

		if (version != previousVersion && enableAnalytics.getBoolean(true))
			analyticsHelper.start();
	}

	void registerOther() {
		if (((ConfigValue<Boolean>) getConfigValue("doGenerateStructures")).getValue()) {
			LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft structures...");
			MinecraftForge.TERRAIN_GEN_BUS.register(LanteaCraft.getInstance());
			try {
				if (new CallableMinecraftVersion(null).minecraftVersion().equals("1.6.4"))
					MapGenStructureIO.func_143031_a(FeatureUnderDesertPyramid.class, "LanteaCraft:DesertPyramid");
			} catch (Throwable e) {
				LanteaCraft.getLogger().log(Level.FINE, "Could not register structure type LanteaCraft:DesertPyramid", e);
			}
		}
	}

	void registerBlocks() {
		LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft blocks...");
		Blocks.sgRingBlock = (BlockStargateRing) registerBlock(BlockStargateRing.class, ItemStargateRing.class,
				LanteaNameRegistry.getBlockMapping("blockRing"), "stargateRing", "Stargate Ring Segment");
		// Blocks.sgPegasusRingBlock = (BlockPegasusStargateRing)
		// registerBlock(BlockPegasusStargateRing.class,
		// ItemPegasusStargateRing.class,
		// LanteaNameRegistry.getBlockMapping("blockPegasusRing"),
		// "stargatePegasusRing", "Pegasus Stargate Ring Segment");

		Blocks.sgBaseBlock = (BlockStargateBase) registerBlock(BlockStargateBase.class, ItemBlock.class,
				LanteaNameRegistry.getBlockMapping("blockBase"), "stargateBase", "Stargate Base");
		// Blocks.sgPegasusBaseBlock = (BlockPegasusStargateBase)
		// registerBlock(BlockPegasusStargateBase.class, ItemBlock.class,
		// LanteaNameRegistry.getBlockMapping("blockPegasusBase"),
		// "stargatePegasusBase", "Pegasus Stargate Base");

		Blocks.sgControllerBlock = (BlockStargateController) registerBlock(BlockStargateController.class,
				ItemBlock.class, LanteaNameRegistry.getBlockMapping("blockController"), "stargateController",
				"Stargate Controller");
		// Blocks.sgPegasusControllerBlock = (BlockPegasusStargateController)
		// registerBlock(BlockPegasusStargateController.class, ItemBlock.class,
		// LanteaNameRegistry.getBlockMapping("blockPegasusController"),
		// "stargatePegasusController", "Pegasus Stargate Controller");

		Blocks.naquadahBlock = registerBlock(BlockNaquadah.class, ItemBlock.class,
				LanteaNameRegistry.getBlockMapping("blockNaquadah"), "naquadahBlock", "Naquadah Alloy Block");
		Blocks.naquadahOre = registerBlock(BlockNaquadahOre.class, ItemBlock.class,
				LanteaNameRegistry.getBlockMapping("oreNaquadah"), "naquadahOre", "Naquadah Ore");
	}

	public Block registerBlock(Class<? extends Block> classOf, Class<? extends ItemBlock> itemClassOf,
			String idForName, String unlocalizedName, String localizedName) {
		LanteaCraft.getLogger().log(Level.FINE, "Attempting to register block " + idForName);
		try {
			int id = config.getBlock(unlocalizedName, 4095).getInt();
			Constructor<? extends Block> ctor = classOf.getConstructor(int.class);
			Block block = ctor.newInstance(id);
			block.setUnlocalizedName(LanteaCraft.getInstance().getAssetKey() + ":" + unlocalizedName);
			block.setTextureName(LanteaCraft.getInstance().getAssetKey() + ":" + unlocalizedName + "_"
					+ getRenderMode());
			block.setCreativeTab(LanteaCraft.getInstance().getCreativeTab());
			GameRegistry.registerBlock(block, itemClassOf, idForName);
			LanguageRegistry.addName(block, localizedName);
			return block;
		} catch (Exception e) {
			LanteaCraft.getLogger().log(Level.SEVERE, "Failed to register block, an exception occured.", e);
			throw new RuntimeException(e);
		}
	}

	public Item registerItem(Class<? extends Item> classOf, String idForName, String unlocalizedName,
			String localizedName) {
		LanteaCraft.getLogger().log(Level.FINE, "Attempting to register item " + idForName);
		try {
			int id = config.getItem(unlocalizedName, 31743).getInt();
			Constructor<? extends Item> ctor = classOf.getConstructor(int.class);
			Item item = ctor.newInstance(id);
			item.setUnlocalizedName(LanteaCraft.getInstance().getAssetKey() + ":" + unlocalizedName);
			item.setTextureName(LanteaCraft.getInstance().getAssetKey() + ":" + unlocalizedName + "_" + getRenderMode());
			item.setCreativeTab(LanteaCraft.getInstance().getCreativeTab());
			GameRegistry.registerItem(item, idForName);
			LanguageRegistry.addName(item, localizedName);
			return item;
		} catch (Exception e) {
			LanteaCraft.getLogger().log(Level.SEVERE, "Failed to register item, an exception occured.", e);
			throw new RuntimeException(e);
		}
	}

	void registerItems() {
		LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft items...");
		Items.naquadah = registerItem(Item.class, LanteaNameRegistry.getBlockMapping("itemNaquadah"), "naquadah",
				"Naquadah");
		Items.naquadahIngot = registerItem(Item.class, LanteaNameRegistry.getBlockMapping("itemNaquadahIngot"),
				"naquadahIngot", "Naquadah Alloy Ingot");
		Items.sgCoreCrystal = registerItem(Item.class, LanteaNameRegistry.getBlockMapping("itemCoreCrystal"),
				"sgCoreCrystal", "Stargate Core Crystal");
		Items.sgControllerCrystal = registerItem(Item.class,
				LanteaNameRegistry.getBlockMapping("itemControllerCrystal"), "sgControllerCrystal",
				"Stargate Controller Crystal");

		Items.tokraSpawnEgg = (ItemTokraSpawnEgg) registerItem(ItemTokraSpawnEgg.class,
				LanteaNameRegistry.getItemMapping("tokraSpawnEgg"), "tokraSpawnEgg", "Tok'ra Spawn Egg");

		Items.debugger = (ItemDebugTool) registerItem(ItemDebugTool.class, "lanteadebug", "lanteadebug",
				"LanteaCraft Debugger");

	}

	void registerOres() {
		LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft ores...");
		registerOre("oreNaquadah", Blocks.naquadahOre);
		registerOre("naquadah", Items.naquadah);
		registerOre("ingotNaquadahAlloy", Items.naquadahIngot);
	}

	void registerRecipes() {
		LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft recipes...");
		ItemStack chiselledSandstone = new ItemStack(Block.sandStone, 1, 1);
		ItemStack smoothSandstone = new ItemStack(Block.sandStone, 1, 2);
		ItemStack sgChevronBlock = new ItemStack(Blocks.sgRingBlock, 1, 1);
		ItemStack blueDye = new ItemStack(Item.dyePowder, 1, 4);
		ItemStack orangeDye = new ItemStack(Item.dyePowder, 1, 14);
		if (config.getBoolean("options", "allowCraftingNaquadah", false))
			newShapelessRecipe(Items.naquadah, 1, Item.coal, Item.slimeBall, Item.blazePowder);
		newRecipe(Blocks.sgRingBlock, 1, "ICI", "NNN", "III", 'I', Item.ingotIron, 'N', "ingotNaquadahAlloy", 'C',
				chiselledSandstone);
		newRecipe(sgChevronBlock, "CgC", "NpN", "IrI", 'I', Item.ingotIron, 'N', "ingotNaquadahAlloy", 'C',
				chiselledSandstone, 'g', Item.glowstone, 'r', Item.redstone, 'p', Item.enderPearl);
		newRecipe(Blocks.sgBaseBlock, 1, "CrC", "NeN", "IcI", 'I', Item.ingotIron, 'N', "ingotNaquadahAlloy", 'C',
				chiselledSandstone, 'r', Item.redstone, 'e', Item.eyeOfEnder, 'c', Items.sgCoreCrystal);
		newRecipe(Blocks.sgControllerBlock, 1, "bbb", "OpO", "OcO", 'b', Block.stoneButton, 'O', Block.obsidian, 'p',
				Item.enderPearl, 'r', Item.redstone, 'c', Items.sgControllerCrystal);
		newShapelessRecipe(Items.naquadahIngot, 1, "naquadah", Item.ingotIron);
		newRecipe(Blocks.naquadahBlock, 1, "NNN", "NNN", "NNN", 'N', "ingotNaquadahAlloy");
		newRecipe(Items.naquadahIngot, 9, "B", 'B', Blocks.naquadahBlock);
		if (config.getBoolean("options", "allowCraftingCrystals", false)) {
			newRecipe(Items.sgCoreCrystal, 1, "bbr", "rdb", "brb", 'b', blueDye, 'r', Item.redstone, 'd', Item.diamond);
			newRecipe(Items.sgControllerCrystal, 1, "roo", "odr", "oor", 'o', orangeDye, 'r', Item.redstone, 'd',
					Item.diamond);
		}
	}

	void registerContainers() {
		LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft containers...");
		addContainer(EnumGuiList.SGBase.ordinal(), ContainerStargateBase.class);
	}

	void registerRandomItems() {
		LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft random drop items...");
		String[] categories = { ChestGenHooks.MINESHAFT_CORRIDOR, ChestGenHooks.PYRAMID_DESERT_CHEST,
				ChestGenHooks.PYRAMID_JUNGLE_CHEST, ChestGenHooks.STRONGHOLD_LIBRARY, ChestGenHooks.VILLAGE_BLACKSMITH };
		addRandomChestItem(new ItemStack(Blocks.sgBaseBlock), 1, 1, 2, categories);
		addRandomChestItem(new ItemStack(Blocks.sgControllerBlock), 1, 1, 1, categories);
		addRandomChestItem(new ItemStack(Blocks.sgRingBlock, 1, 0), 1, 3, 8, categories);
		addRandomChestItem(new ItemStack(Blocks.sgRingBlock, 1, 1), 1, 3, 7, categories);
		addRandomChestItem(new ItemStack(Items.sgCoreCrystal, 1, 0), 1, 1, 2, categories);
		addRandomChestItem(new ItemStack(Items.sgControllerCrystal, 1, 0), 1, 1, 1, categories);
	}

	void registerWorldGenerators() {
		if (config.getBoolean("options", "enableNaquadahOre", true)) {
			LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft NaquadahOre generator...");
			naquadahOreGenerator = new NaquadahOreWorldGen();
			GameRegistry.registerWorldGenerator(naquadahOreGenerator);
		}
	}

	void registerVillagers() {
		LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft Tokra villagers...");
		tokraVillagerID = addVillager(config.getVillager("tokra"), "tokra",
				LanteaCraft.getResource("textures/skins/tokra.png"));
		addTradeHandler(tokraVillagerID, new TradeHandler());
	}

	void registerTileEntities() {
		LanteaCraft.getLogger().log(Level.FINE, "Registering LanteaCraft tile entities...");
		GameRegistry.registerTileEntity(TileEntityStargateBase.class,
				LanteaNameRegistry.getTileEntityMapping("tileEntityBase"));
		GameRegistry.registerTileEntity(TileEntityStargateRing.class,
				LanteaNameRegistry.getTileEntityMapping("tileEntityRing"));
		GameRegistry.registerTileEntity(TileEntityStargateController.class,
				LanteaNameRegistry.getTileEntityMapping("tileEntityController"));
	}

	public ConfigValue<?> getConfigValue(String name) {
		LanteaCraft.getLogger().log(Level.FINE, "Fetching configuration value `" + name + "`");
		for (ConfigValue<?> item : configValues)
			if (item.getName().equalsIgnoreCase(name))
				return item;
		return null;
	}

	public int getRenderMode() {
		int mode = ((ConfigValue<Integer>) getConfigValue("renderQuality")).getValue();
		if (mode <= 32)
			return 32;
		if (mode > 32 && mode <= 64)
			return 64;
		if (mode > 64 && mode <= 128)
			return 128;
		return 32; // invalid value?
	}

	public boolean isUsingModels() {
		return ((ConfigValue<Boolean>) getConfigValue("renderUseModels")).getValue();
	}

	Container getGuiContainer(int id, EntityPlayer player, World world, int x, int y, int z) {
		return null;
	}

	Object createGuiElement(Class cls, EntityPlayer player, World world, int x, int y, int z) {
		try {
			try {
				return cls.getMethod("create", EntityPlayer.class, World.class, int.class, int.class, int.class)
						.invoke(null, player, world, x, y, z);
			} catch (NoSuchMethodException e) {
				return cls.getConstructor(EntityPlayer.class, World.class, int.class, int.class, int.class)
						.newInstance(player, world, x, y, z);
			}
		} catch (Exception e) {
			LanteaCraft.getLogger().log(Level.SEVERE, "Failed to create GUI element, an exception occured.", e);
			Throwable cause = e.getCause();
			if (cause != null)
				cause.printStackTrace();
			else
				e.printStackTrace();
			return null;
		}
	}

	public int addVillager(int id, String name, ResourceLocation skin) {
		LanteaCraft.getLogger().log(Level.FINE, "Adding villager ID " + id + " with name " + name);
		VSBinding b = new VSBinding();
		b.id = id;
		b.object = skin;
		registeredVillagers.put(name, b);
		return id;
	}

	public void addTradeHandler(int villagerID, IVillageTradeHandler handler) {
		LanteaCraft.getLogger().log(Level.FINE, "Registering trade handler for villager ID " + villagerID);
		VillagerRegistry.instance().registerVillageTradeHandler(villagerID, handler);
	}

	public void registerOre(String name, Block block) {
		registerOre(name, new ItemStack(block));
	}

	public void registerOre(String name, Item item) {
		registerOre(name, new ItemStack(item));
	}

	public void registerOre(String name, ItemStack item) {
		LanteaCraft.getLogger().log(Level.FINE, "Registering ore with name " + name);
		OreDictionary.registerOre(name, item);
	}

	public void newRecipe(Item product, int qty, Object... params) {
		newRecipe(new ItemStack(product, qty), params);
	}

	public void newRecipe(Block product, int qty, Object... params) {
		newRecipe(new ItemStack(product, qty), params);
	}

	public void newRecipe(ItemStack product, Object... params) {
		LanteaCraft.getLogger().log(Level.FINE, "Registering new generic recipe");
		GameRegistry.addRecipe(new ShapedOreRecipe(product, params));
	}

	public void newShapelessRecipe(Item product, int qty, Object... params) {
		newShapelessRecipe(new ItemStack(product, qty), params);
	}

	public void newShapelessRecipe(ItemStack product, Object... params) {
		LanteaCraft.getLogger().log(Level.FINE, "Registering new generic shapeless recipe");
		GameRegistry.addRecipe(new ShapelessOreRecipe(product, params));
	}

	public void addContainer(int id, Class<? extends Container> cls) {
		LanteaCraft.getLogger().log(Level.FINE,
				"Registering container with ID " + id + ", class " + cls.getCanonicalName());
		registeredContainers.put(id, cls);
	}

	public void addRandomChestItem(ItemStack stack, int minQty, int maxQty, int weight, String... category) {
		WeightedRandomChestContent item = new WeightedRandomChestContent(stack, minQty, maxQty, weight);
		for (String element : category) {
			LanteaCraft.getLogger().log(Level.FINE, "Adding new WeightedRandomChestContent for element " + element);
			ChestGenHooks.addItem(element, item);
		}
	}

	public NaquadahOreWorldGen getOreGenerator() {
		return naquadahOreGenerator;
	}

	public Class<? extends Container> getContainer(int id) {
		return registeredContainers.get(id);
	}

	public Object getGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return null;
	}

	public Class<? extends GuiScreen> getGUI(int id) {
		return registeredGUIs.get(id);
	}

	public int getVillagerID(String name) {
		VSBinding villager = registeredVillagers.get(name);
		if (villager != null)
			return villager.id;
		return 0;
	}

	public void onWorldLoad(Load e) {
		// TODO Auto-generated method stub
		LanteaCraft.getLogger().log(Level.WARNING, "Herp derp provider.load: " + e.world.getProviderName());
	}

	public void onWorldUnload(Unload e) {
		// TODO Auto-generated method stub
		LanteaCraft.getLogger().log(Level.WARNING, "Herp derp provider.unload: " + e.world.getProviderName());
	}

	public void onWorldSave(Save e) {
		// TODO Auto-generated method stub
		LanteaCraft.getLogger().log(Level.WARNING, "Herp derp provider.save: " + e.world.getProviderName());
	}

	public void handlePacket(LanteaPacket packet, Player player) {
		if (packet.getPacketIsForServer())
			defaultServerPacketHandler.handlePacket(packet, player);
		else
			return;
	}

	public void sendToServer(LanteaPacket packet) {
		throw new RuntimeException("Cannot send to server: this method was not overridden!!");
	}

	public void sendToAllPlayers(LanteaPacket packet) {
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		if (server != null) {
			LanteaCraft.getLogger().log(Level.INFO, "LanteaCraft sending packet to all players: " + packet.toString());
			server.getConfigurationManager().sendPacketToAllPlayers(packet.toPacket());
		}
	}

	public void sendToPlayer(EntityPlayer player, LanteaPacket packet) {
		PacketDispatcher.sendPacketToPlayer(packet.toPacket(), (Player) player);
	}

}