package com.legacy.aether.server.player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import com.legacy.aether.server.AetherConfig;
import com.legacy.aether.server.blocks.BlocksAether;
import com.legacy.aether.server.containers.inventory.InventoryAccessories;
import com.legacy.aether.server.entities.passive.EntityMiniCloud;
import com.legacy.aether.server.entities.passive.mountable.EntityParachute;
import com.legacy.aether.server.items.ItemsAether;
import com.legacy.aether.server.networking.AetherNetworkingManager;
import com.legacy.aether.server.networking.packets.PacketAccessory;
import com.legacy.aether.server.player.abilities.Ability;
import com.legacy.aether.server.player.abilities.AbilityAccessories;
import com.legacy.aether.server.player.abilities.AbilityArmor;
import com.legacy.aether.server.player.abilities.AbilityFlight;
import com.legacy.aether.server.player.abilities.AbilityRepulsion;
import com.legacy.aether.server.player.capability.PlayerAetherManager;
import com.legacy.aether.server.player.movement.AetherPoisonMovement;
import com.legacy.aether.server.player.perks.AetherRankings;
import com.legacy.aether.server.player.perks.util.DonatorMoaSkin;
import com.legacy.aether.server.registry.achievements.AchievementsAether;
import com.legacy.aether.server.world.TeleporterAether;

public class PlayerAether
{

	public EntityPlayer thePlayer;

	private UUID healthUUID = UUID.fromString("df6eabe7-6947-4a56-9099-002f90370706");

	private AttributeModifier healthModifier;

	public InventoryAccessories accessories;

	private AetherPoisonMovement poison;

	public float wingSinage;

	public EntityMiniCloud leftCloud, rightCloud;

	public Entity currentBoss;

	public Ability[] abilities;

	private boolean isJumping, isDonator;

	private float lifeShardsUsed;

	public float timeInPortal, prevTimeInPortal;

	public boolean hasTeleported = false;

	private String cooldownName = "Hammer of Notch";

	private int cooldown, cooldownMax;

	public boolean shouldRenderHalo = true;

	public DonatorMoaSkin donatorMoaSkin;

	public List<Item> extendedReachItems = Arrays.asList(new Item[] {ItemsAether.valkyrie_shovel, ItemsAether.valkyrie_pickaxe, ItemsAether.valkyrie_axe});

	public PlayerAether() { }

	public PlayerAether(EntityPlayer player)
	{
		this.thePlayer = player;

		this.donatorMoaSkin = new DonatorMoaSkin();
		this.poison = new AetherPoisonMovement(this);
		this.accessories = new InventoryAccessories(this);

		this.abilities = new Ability [] {new AbilityArmor(this), new AbilityAccessories(this), new AbilityFlight(this), new AbilityRepulsion(this)};
	}

	public static PlayerAether get(EntityPlayer player) 
	{
		return (PlayerAether) player.getCapability(PlayerAetherManager.AETHER_PLAYER, null);
	}

	public void onUpdate()
	{
		for (Ability ability : this.abilities)
		{
			if (ability.isEnabled())
			{
				ability.onUpdate();
			}
		}

		this.poison.onUpdate();

		if (this.leftCloud != null && this.rightCloud != null)
		{
			if (this.leftCloud.isDead && this.rightCloud.isDead)
			{
				this.leftCloud = new EntityMiniCloud(this.thePlayer.worldObj, this.thePlayer, 0);
				this.rightCloud = new EntityMiniCloud(this.thePlayer.worldObj, this.thePlayer, 1);
			}
		}

		if (this.wingSinage > 3.141593F * 2F)
		{
			this.wingSinage -= 3.141593F * 2F;
		}
		else
		{
			this.wingSinage += 0.1F;
		}

		if (this.currentBoss != null)
		{
			if (((EntityLiving)this.currentBoss).getHealth() <= 0 || this.currentBoss.isDead || Math.sqrt(Math.pow(currentBoss.posX - this.thePlayer.posX, 2) + Math.pow(currentBoss.posY - this.thePlayer.posY, 2) + Math.pow(currentBoss.posZ - this.thePlayer.posZ, 2)) > 50)
			{
				this.currentBoss = null;
			}
		}

		boolean hasJumped = ReflectionHelper.getPrivateValue(EntityLivingBase.class, this.thePlayer, "isJumping", "field_70703_bu");

		this.setJumping(hasJumped);

		if (this.getCooldown() > 0)
		{
			this.setCooldown(this.getCooldown() - 1);
		}

		this.prevTimeInPortal = this.timeInPortal;

		if (this.isInBlock(BlocksAether.aether_portal))
		{
			this.timeInPortal += 0.0125F;

			if (!this.hasTeleported && (this.thePlayer.capabilities.isCreativeMode || this.timeInPortal < 1.5F && this.timeInPortal >= 1.0F))
			{
				this.teleportPlayer(true);
			}
		}
		else
		{
			if (this.timeInPortal > 0.0F)
			{
				this.timeInPortal -= 0.05F;
			}

			if (this.timeInPortal < 0.0F)
			{
				this.timeInPortal = 0.0F;
			}

			this.hasTeleported = false;
		}

		if (this.thePlayer.motionY < -2F)
		{
			EntityParachute parachute = null;

			if(this.thePlayer.inventory.hasItemStack(new ItemStack(ItemsAether.cloud_parachute)))
			{
				parachute = new EntityParachute(this.thePlayer.worldObj, this.thePlayer, false);
				parachute.setPosition(this.thePlayer.posX, this.thePlayer.posY, this.thePlayer.posZ);
				this.thePlayer.worldObj.spawnEntityInWorld(parachute);
				this.thePlayer.inventory.deleteStack(new ItemStack(ItemsAether.cloud_parachute));
			}
			else
			{
				if (this.thePlayer.inventory.hasItemStack(new ItemStack(ItemsAether.golden_parachute)))
				{
					for(int i = 0; i < this.thePlayer.inventory.getSizeInventory(); i++)
					{
						ItemStack itemstack = this.thePlayer.inventory.getStackInSlot(i);

						if(itemstack != null && itemstack.getItem() == ItemsAether.golden_parachute)
						{ 
							itemstack.damageItem(1, this.thePlayer);
							parachute = new EntityParachute(this.thePlayer.worldObj, this.thePlayer, true);
							parachute.setPosition(this.thePlayer.posX, this.thePlayer.posY, this.thePlayer.posZ);
							this.thePlayer.inventory.setInventorySlotContents(i, itemstack);
							this.thePlayer.worldObj.spawnEntityInWorld(parachute);
						}
					}
				}
			}
		}

		if (this.thePlayer.dimension == AetherConfig.getAetherDimensionID())
		{
			if (this.thePlayer.posY < -2)
			{
				this.thePlayer.dismountRidingEntity();
				this.teleportPlayer(false);
				this.thePlayer.setPositionAndUpdate(thePlayer.posX, 256, thePlayer.posZ);
			}

			this.thePlayer.addStat(AchievementsAether.enter_aether);
		}

		if (!this.thePlayer.worldObj.isRemote)
		{
			((EntityPlayerMP) this.thePlayer).interactionManager.setBlockReachDistance(this.getReach());
		}
	}

	public boolean onPlayerAttacked(DamageSource source)
	{
		if (this.isWearingPhoenixSet() && source.isFireDamage())
		{
			return true;
		}

		return false;
	}

	public void onPlayerDeath()
	{
		if (!this.thePlayer.worldObj.getGameRules().getBoolean("keepInventory"))
		{
			this.accessories.dropAllItems();
		}
	}

	public void onPlayerRespawn()
	{
		this.refreshMaxHP();

		this.thePlayer.setHealth(this.thePlayer.getMaxHealth());

		this.updateAccessories();
	}

	public void onChangedDimension(int toDim, int fromDim)
	{
		this.updateAccessories();
	}

	public void saveNBTData(NBTTagCompound output) 
	{
		if (AetherRankings.isRankedPlayer(this.thePlayer.getUniqueID()))
		{
			output.setBoolean("halo", this.shouldRenderHalo);
		}

		output.setInteger("hammer_cooldown", this.cooldown);
		output.setString("notch_hammer_name", this.cooldownName);
		output.setInteger("max_hammer_cooldown", this.cooldownMax);
		output.setFloat("shards_used", this.lifeShardsUsed);
		output.setTag("accessories", this.accessories.writeToNBT(new NBTTagList()));
	}

	public void loadNBTData(NBTTagCompound input)
	{
		if (input.hasKey("halo"))
		{
			this.shouldRenderHalo = input.getBoolean("halo");
		}

		this.hasTeleported = true;
		this.cooldown = input.getInteger("hammer_cooldown");
		this.cooldownName = input.getString("notch_hammer_name");
		this.cooldownMax = input.getInteger("max_hammer_cooldown");
		this.lifeShardsUsed = input.getFloat("shards_used");
		this.accessories.readFromNBT(input.getTagList("accessories", 10));
	}

	/*
	 * Gets the custom speed at the current point in time
	 */
	public float getCurrentPlayerStrVsBlock(float original) 
	{ 
		float f = original;

		if(this.wearingAccessory(ItemsAether.zanite_pendant))
		{
			f *= (1F + ((float)(this.accessories.getStackFromItem(ItemsAether.zanite_pendant).getItemDamage()) / ((float)(this.accessories.getStackFromItem(ItemsAether.zanite_pendant).getMaxDamage()) * 3F)));
		}

		if(this.wearingAccessory(ItemsAether.zanite_ring))
		{
			f *= (1F + ((float)(this.accessories.getStackFromItem(ItemsAether.zanite_ring).getItemDamage()) / ((float)(this.accessories.getStackFromItem(ItemsAether.zanite_ring).getMaxDamage()) * 3F)));
		}

		return f == original ? original : f + original; 
	}

	/*
	 * Gets the player reach at the current point in time
	 */
	public double getReach()
	{
		ItemStack stack = this.thePlayer.inventory.getCurrentItem();
		
		if (stack != null && this.extendedReachItems.contains(stack.getItem()))
		{
			return 10.0D;
		}

		return this.thePlayer.capabilities.isCreativeMode ? 5.0F : 4.5F;
	}

	/*
	 * Checks if the player is wearing the specified item as an accessory
	 */
	public boolean wearingAccessory(Item item)
	{
		for (int index = 0; index < 8; index++)
		{
			if (this.getAccessoryStacks()[index] != null && this.getAccessoryStacks()[index].getItem() == item)
			{
				return true;
			}
		}

		return false;
	}

	/*
	 * Checks how many of the specific item the player is wearing (If any)
	 */
	public int getAccessoryCount(Item item)
	{
		int count = 0;

		for (int index = 0; index < 8; index++)
		{
			if (this.getAccessoryStacks()[index] != null && this.getAccessoryStacks()[index].getItem() == item)
			{
				count++;
			}
		}

		return count;
	}

	/*
	 * Checks if the player is wearing the specified item as armor
	 */
	public boolean wearingArmor(Item itemID)
	{
		for (int index = 0; index < 4; index++)
		{
			if (this.thePlayer != null && this.thePlayer.inventory.armorInventory[index] != null && this.thePlayer.inventory.armorInventory[index].getItem() == itemID)
			{
				return true;
			}
		}

		return false;
	}

	/*
	 * Checks of the player is wearing a full set of Zanite
	 */
	public boolean isWearingZaniteSet()
	{
		return wearingArmor(ItemsAether.zanite_helmet) && wearingArmor(ItemsAether.zanite_chestplate) && wearingArmor(ItemsAether.zanite_leggings) && wearingArmor(ItemsAether.zanite_boots) && wearingAccessory(ItemsAether.zanite_gloves);
	}

	/*
	 * Checks of the player is wearing a full set of Gravitite
	 */
	public boolean isWearingGravititeSet()
	{
		return wearingArmor(ItemsAether.gravitite_helmet) && wearingArmor(ItemsAether.gravitite_chestplate) && wearingArmor(ItemsAether.gravitite_leggings) && wearingArmor(ItemsAether.gravitite_boots) && wearingAccessory(ItemsAether.gravitite_gloves);
	}

	/*
	 * Checks of the player is wearing a full set of Neptune
	 */
	public boolean isWearingNeptuneSet()
	{
		return wearingArmor(ItemsAether.neptune_helmet) && wearingArmor(ItemsAether.neptune_chestplate) && wearingArmor(ItemsAether.neptune_leggings) && wearingArmor(ItemsAether.neptune_boots) && wearingAccessory(ItemsAether.neptune_gloves);
	}

	/*
	 * Checks of the player is wearing a full set of Phoenix
	 */
	public boolean isWearingPhoenixSet()
	{
		return wearingArmor(ItemsAether.phoenix_helmet) && wearingArmor(ItemsAether.phoenix_chestplate) && wearingArmor(ItemsAether.phoenix_leggings) && wearingArmor(ItemsAether.phoenix_boots) && wearingAccessory(ItemsAether.phoenix_gloves);
	}

	/*
	 * Checks of the player is wearing a full set of Valkyrie
	 */
	public boolean isWearingValkyrieSet()
	{
		return wearingArmor(ItemsAether.valkyrie_helmet) && wearingArmor(ItemsAether.valkyrie_chestplate) && wearingArmor(ItemsAether.valkyrie_leggings) && wearingArmor(ItemsAether.valkyrie_boots) && wearingAccessory(ItemsAether.valkyrie_gloves);
	}

	/*
	 * Checks of the player is wearing a full set of Obsidian
	 */
	public boolean isWearingObsidianSet()
	{
		return wearingArmor(ItemsAether.obsidian_helmet) && wearingArmor(ItemsAether.obsidian_chestplate) && wearingArmor(ItemsAether.obsidian_leggings) && wearingArmor(ItemsAether.obsidian_boots) && wearingAccessory(ItemsAether.obsidian_gloves);
	}

	/*
	 * Instance of the accessories
	 */
	public ItemStack[] getAccessoryStacks() 
	{
		return this.accessories.stacks;
	}

	/*
	 * The teleporter which sends the player to the Aether/Overworld
	 */
	private void teleportPlayer(boolean shouldSpawnPortal) 
	{
		if (this.thePlayer instanceof EntityPlayerMP)
		{
			EntityPlayerMP player = (EntityPlayerMP) this.thePlayer;
			PlayerList scm = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();

			int transferToID = player.dimension == AetherConfig.getAetherDimensionID() ? 0 : AetherConfig.getAetherDimensionID();

			scm.transferPlayerToDimension(player, transferToID, new TeleporterAether(shouldSpawnPortal, FMLCommonHandler.instance().getMinecraftServerInstance().worldServerForDimension(transferToID)));
		}

		this.hasTeleported = true;
		this.timeInPortal = 0.0F;
	}

	/*
	 * A checker to see if a player is inside a block or not
	 */
	public boolean isInBlock(Block blockID)
	{
		int x = MathHelper.floor_double(this.thePlayer.posX);
		int y = MathHelper.floor_double(this.thePlayer.posY);
		int z = MathHelper.floor_double(this.thePlayer.posZ);
		BlockPos pos = new BlockPos(x, y, z);

		return this.thePlayer.worldObj.getBlockState(pos).getBlock() == blockID || this.thePlayer.worldObj.getBlockState(pos.up()).getBlock() == blockID || this.thePlayer.worldObj.getBlockState(pos.down()).getBlock() == blockID;
	}

	/*
	 * Increases the maximum amount of HP (Caps at 20)
	 */
	public void increaseMaxHP()
	{
		this.lifeShardsUsed = this.lifeShardsUsed + 2F;
		this.healthModifier = new AttributeModifier(healthUUID, "Aether Health Modifier", this.lifeShardsUsed, 0);

		if (this.thePlayer.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getModifier(healthUUID) != null)
		{
			this.thePlayer.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).removeModifier(this.healthModifier);
		}

		this.thePlayer.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(this.healthModifier);
	}

	/*
	 * An updater to update the players HP
	 */
	public void refreshMaxHP()
	{
		this.healthModifier = new AttributeModifier(healthUUID, "Aether Health Modifier", this.lifeShardsUsed, 0);

		if (this.thePlayer.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getModifier(healthUUID) != null)
		{
			this.thePlayer.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).removeModifier(this.healthModifier);
		}

		this.thePlayer.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(this.healthModifier);
	}

	/*
	 * Instance of the current extra health the player has
	 */
	public float getExtraHealth()
	{
		return this.lifeShardsUsed;
	}

	/*
	 * Instance of the boss the player is fighting
	 */
	public Entity getCurrentBoss()
	{
		return this.currentBoss;
	}

	/*
	 * Sets the boss the player is fighting
	 */
	public void setCurrentBoss(Entity currentBoss)
	{
		this.currentBoss = currentBoss;
	}

	/*
	 * Sets the cooldown and name of the players hammer
	 */
	public boolean setGeneralCooldown(int cooldown, String stackName)
	{
		if (this.cooldown == 0)
		{
			this.cooldown = cooldown;
			this.cooldownMax = cooldown;
			this.cooldownName = stackName;

			return true;
		}
		else
		{
			return false;
		}
	}

	/*
	 * Instance of the hammers cooldown
	 */
	public int getCooldown()
	{
		return this.cooldown;
	}

	/*
	 * Sets the cooldown of the players hammer
	 */
	public void setCooldown(int cooldown)
	{
		this.cooldown = cooldown;
	}

	/*
	 * The max cooldown of the players hammer
	 */
	public int getCooldownMax()
	{
		return this.cooldownMax;
	}

	/*
	 * The name of the players hammer
	 */
	public String getCooldownName()
	{
		return this.cooldownName;
	}

	/*
	 * Instance of the poison used to move the player
	 */
	public AetherPoisonMovement poisonInstance()
	{
		return this.poison;
	}

	/*
	 * Afflicts a set amount of poison to the player
	 */
	public void afflictPoison()
	{
		this.poison.afflictPoison();
	}

	/*
	 * Afflicts a set amount of remedy to the player
	 */
	public void attainCure(int time)
	{
		this.poison.curePoison(time);
	}

	/*
	 * A checker to tell if the player is poisoned or not
	 */
	public boolean isPoisoned()
	{
		return this.poison.poisonTime > 0;
	}

	/*
	 * A checker to tell if the player is curing or not
	 */
	public boolean isCured()
	{
		return this.poison.poisonTime < 0;
	}

	/*
	 * Checks if the player is jumping or not
	 */
	public boolean isJumping()
	{
		return this.isJumping;
	}

	/*
	 * Sets if the player is jumping or not
	 */
	public void setJumping(boolean isJumping)
	{
		this.isJumping = isJumping;
	}

	/*
	 * Checks if the player is a donator or not
	 */
	public boolean isDonator()
	{
		return this.isDonator;
	}

	/*
	 * Sets if the player is a donator or not
	 */
	public void setDonator(boolean isDonator)
	{
		this.isDonator = isDonator;
	}

	/*
	 * Updates Player accessories
	 */
	public void updateAccessories()
	{
		if (!this.thePlayer.worldObj.isRemote)
		{
			AetherNetworkingManager.sendToAll(new PacketAccessory(this));
		}
	}
}