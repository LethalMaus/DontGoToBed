package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dontgotobed.composeapp.generated.resources.*
import org.jetbrains.compose.resources.imageResource
import kotlin.math.*

@Composable
internal fun EquipmentIcon(item: ItemType, modifier: Modifier = Modifier, tint: Color? = null) {
    if(item==ItemType.ElectricArrow) { GameIcon(GameIconKind.ElectricArrow,tr("Electric arrow"),modifier,tint=Color.Yellow);return }
    val index = starterEquipment.indexOf(item)
    if (index < 0) return
    val bitmap = imageResource(Res.drawable.equipment_sheet)
    val painter = remember(bitmap,index) { BitmapPainter(bitmap,IntOffset(index%3*512,index/3*512),IntSize(512,512),FilterQuality.None) }
    val colourFilter = when(tint) {
        ArmourColour.Green.tint -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            1f,0f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,0f,1f,0f)))
        ArmourColour.Red.tint -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0f,0f,1f,0f,0f, 1f,0f,0f,0f,0f, 1f,0f,0f,0f,0f, 0f,0f,0f,1f,0f)))
        else -> null
    }
    Image(painter,null,modifier,contentScale=ContentScale.Fit,colorFilter=colourFilter)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InventoryDialog(bag: PlayerInventory, selected: Int, equipment: PlayerEquipment, onSelect: (Int)->Unit, onClose: ()->Unit) {
    @Composable fun items() {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            bag.slots.forEachIndexed { i,slot ->
                val eq=slot.type?.let { equipment.loadout.equipped(it) }==true
                Surface(onClick={onSelect(i)},color=if(i==selected) Color(0xFF416476) else Color(0xFF10243A),
                    border=BorderStroke(2.dp,if(eq) Color(0xFFFFCE66) else Color(0xFF6D8796)),shape=RoundedCornerShape(8.dp),
                    modifier=Modifier.width(96.dp).heightIn(min=100.dp).semantics {
                        this.selected=i==selected
                        contentDescription=tr(if (eq) "Select {0}, {1}, equipped" else "Select {0}, {1}", slot.displayName, slot.count)
                    }) {
                    Column(Modifier.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp,Alignment.CenterVertically)) {
                        if(slot.type?.isEquipment==true || slot.type==ItemType.ElectricArrow) EquipmentIcon(slot.type,Modifier.size(32.dp))
                        else if(slot.type==ItemType.Block) BlockMaterialIcon(slot.material ?: BlockMaterial.Grass,Modifier.size(32.dp))
                        else if(slot.type!=null) GameIcon(when(slot.type) { ItemType.Arrow->GameIconKind.Arrow;ItemType.Map->GameIconKind.Map;else->GameIconKind.Potion },null,Modifier.size(28.dp),tint=Color.White)
                        Text(if(slot.type==null) tr("Empty") else slot.displayName,fontSize=11.sp,textAlign=TextAlign.Center)
                        Text(if(eq) tr("Equipped") else if(slot.count>0) "×${slot.count}" else "",fontSize=10.sp)
                    }
                }
            }
        }
    }
    @Composable fun details() {
        val slot=bag.slots.getOrNull(selected);val item=slot?.type
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr("Selected: {0}", slot?.displayName ?: tr("Empty")),style=MaterialTheme.typography.titleMedium)
            if(item!=null) Text(inventoryHelp(item),fontSize=13.sp)
            if(item?.isEquipment==true) Button(onClick={equipment.loadout=equipment.loadout.toggle(item);equipment.blocking=false}) {
                Text(if(equipment.loadout.equipped(item)) tr("Unequip") else tr("Equip"))
            }
            if(equipment.loadout.armour) {
                Text(tr("Armour colour"),fontSize=13.sp)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    ArmourColour.entries.forEach { colour->FilterChip(selected=equipment.loadout.colour==colour,
                        onClick={equipment.loadout=equipment.loadout.copy(colour=colour)},label={Text(tr(colour.name))}) }
                }
            }
            Text(tr("Equipped: {0}", starterEquipment.filter { equipment.loadout.equipped(it) }.joinToString { InventorySlot(it,1).displayName }.ifEmpty { tr("None") }),fontSize=12.sp)
            Text(tr("One pair of boots and one hand tool at a time. Equipment is reusable."),fontSize=12.sp)
        }
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),contentAlignment=Alignment.Center) {
            val wide=maxWidth>=480.dp
            Surface(Modifier.widthIn(max=900.dp).fillMaxWidth().heightIn(max=maxHeight),color=Color(0xFF203C52),contentColor=Color.White,shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        GameIcon(GameIconKind.Bag,null,Modifier.padding(end=8.dp).size(28.dp),tint=Color(0xFFFFCE66))
                        Text(tr("Bag · {0}/{1}", bag.slots.count { it.count>0 }, bag.slots.size),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        TextButton(onClick=onClose) { Text(tr("Done"),color=Color(0xFFFFCE66)) }
                    }
                    if(wide) Row(Modifier.weight(1f,fill=false),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(.62f).verticalScroll(rememberScrollState())) { items() }
                        Column(Modifier.weight(.38f).verticalScroll(rememberScrollState())) { details() }
                    } else Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        details();items()
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.EquippedAppearance(loadout: EquipmentLoadout) {
    if(loadout.armour) EquipmentIcon(ItemType.Armour,Modifier.align(Alignment.Center).fillMaxWidth(.62f).fillMaxHeight(.32f),loadout.colour.tint)
    loadout.boots?.let { EquipmentIcon(it,Modifier.align(Alignment.BottomCenter).fillMaxWidth(.65f).fillMaxHeight(.24f)) }
    if(loadout.shield) EquipmentIcon(ItemType.Shield,Modifier.align(Alignment.CenterStart).fillMaxWidth(.48f).fillMaxHeight(.4f))
    if(loadout.hand==ItemType.Gloves) EquipmentIcon(ItemType.Gloves,Modifier.align(Alignment.CenterEnd).fillMaxWidth(.45f).fillMaxHeight(.3f))
}

@Composable
internal fun BoxScope.CompanionLayer(game: CompanionGame, targets: Map<RopeTarget,RopePoint>, playerPoint: RopePoint,
    axis: WorldAxis, span: Int, unit: Dp, centerX: Dp, canAttach: Boolean, paused: Boolean) {
    val slice=floor(playerPoint.other(axis)).toInt().mod(span)
    val c=game.cat
    val bitmap=imageResource(Res.drawable.guide_cat_sheet)
    fun x(point: RopePoint)=centerX+unit*wrappedDelta(playerPoint.along(axis),point.along(axis),span)
    Canvas(Modifier.fillMaxSize()) {
        fun screen(p:RopePoint, anchor:Float=1f)=Offset(x(p).toPx()+unit.toPx()*anchor,size.height-(p.row+anchor)*unit.toPx())
        game.links.forEach { link ->
            val a=game.players()[link.owner] ?: return@forEach
            val b=game.point(link.target) ?: return@forEach
            val visibleA=floor(a.other(axis)).toInt().mod(span)==slice
            val visibleB=floor(b.other(axis)).toInt().mod(span)==slice
            if(!visibleA && !visibleB) return@forEach
            val ea=ropeEndpoint(a,axis,slice,span);val eb=ropeEndpoint(b,axis,slice,span)
            val start=screen(ea.first);val end=screen(eb.first,if(link.target.kind=="C") .5f else 1f)
            val path=Path().apply { moveTo(start.x,start.y);quadraticTo((start.x+end.x)/2,max(start.y,end.y)+12.dp.toPx(),end.x,end.y) }
            drawPath(path,Color(0xFF3F291A),style=Stroke(4.dp.toPx()))
            drawPath(path,Color(0xFFE3BC79),style=Stroke(2.dp.toPx()))
            if(ea.second) drawCircle(Color(0xFFFFCE66),8.dp.toPx(),start,style=Stroke(2.dp.toPx()))
            if(eb.second) drawCircle(Color(0xFFFFCE66),8.dp.toPx(),end,style=Stroke(2.dp.toPx()))
        }
    }
    game.links.filter { it.owner==game.selfId }.forEach { link ->
        game.point(link.target)?.let { point ->
            if(ropeEndpoint(point,axis,slice,span).second) Text(tr("Turn here"),color=Color(0xFFFFCE66),fontSize=11.sp,
                modifier=Modifier.align(Alignment.BottomStart).offset(x=x(point),y=-unit*(point.row+3)).background(Color(0xCC10243A)).padding(3.dp))
        }
    }
    if(c!=null && c.mood!=CatMood.Gone && floor(c.point.other(axis)).toInt().mod(span)==slice) {
        val index=when(c.mood) { CatMood.Sleeping->1; CatMood.Flee->2;else->0 }
        val rect=when(index) { 1->intArrayOf(774,224,610,390);2->intArrayOf(1455,128,700,485);else->intArrayOf(41,118,672,528) }
        val painter=remember(bitmap,index) { BitmapPainter(bitmap,IntOffset(rect[0],rect[1]),IntSize(rect[2],rect[3]),FilterQuality.None) }
        Image(painter,if(c.mood==CatMood.Sleeping) tr("Cat sleeping on the bed") else tr("Guide cat"),
            modifier=Modifier.align(Alignment.BottomStart).offset(x=x(c.point),y=-unit*c.point.row)
                .size(unit)
                .graphicsLayer { scaleX=if(c.facingRight) 1f else -1f }
                .clickable(enabled=canAttach && !paused) { game.request("ATTACH",RopeTarget("C",0)) },contentScale=ContentScale.Fit,alignment=Alignment.BottomCenter)
        if(c.mood==CatMood.Nearby && game.distance(playerPoint,c.point)<4) Text(tr("Purr…"),color=Color.White,fontSize=10.sp,
            modifier=Modifier.align(Alignment.BottomStart).offset(x=x(c.point),y=-unit*(c.point.row+1.1f)))
    }
    if(canAttach && !paused) targets.forEach { (target,point) ->
        if(floor(point.other(axis)).toInt().mod(span)==slice && game.distance(playerPoint,point)<=5f)
            Box(Modifier.align(Alignment.BottomStart).offset(x=x(point),y=-unit*point.row).size(unit*2,unit*3)
                .semantics { contentDescription=tr(if (target.kind == "P") "Rope player {0}" else "Rope enemy {0}", target.id) }
                .clickable(interactionSource = remember(target) { MutableInteractionSource() }, indication = null) { game.request("ATTACH",target) })
    }
}

/** Art replaces managed voxel rendering only; the 3 × 3 × 2 collision bed stays intact. */
@Composable
internal fun BoxScope.HuntBedLayer(bed: HuntBed?, axis: WorldAxis, slice: Int, span: Int,
    unit: Dp, centerX: Dp, playerX: Dp, screenWidth: Dp) {
    if (bed == null) return
    val other = if (axis == WorldAxis.Latitude) bed.longitude else bed.latitude
    if ((slice-other).mod(span) !in 0..2) return
    val along = if (axis == WorldAxis.Latitude) bed.latitude else bed.longitude
    val bitmap = imageResource(Res.drawable.hunt_bed)
    val painter = remember(bitmap) {
        BitmapPainter(bitmap, IntOffset(28,362), IntSize(1481,421), FilterQuality.None)
    }
    val base = centerX + unit*along - playerX
    for (copy in -1..1) {
        val left=base+unit*(span*copy)
        if (left > screenWidth || left+unit*3 < 0.dp) continue
        Image(painter, tr("Bed"), modifier=Modifier.align(Alignment.BottomStart)
            .offset(x=left,y=-unit*bed.row).size(unit*3,unit*2),
            contentScale=ContentScale.FillBounds,
            alpha=.65f+.35f*(bed.health/42f).coerceIn(0f,1f))
    }
}

@Composable
internal fun BoxScope.AlienLayer(game: AlienGame, axis: WorldAxis, slice: Int, span: Int,
    unit: Dp, centerX: Dp, player: RopePoint, reduceMotion: Boolean, shocks: ShockGame) {
    val s=game.state ?: return
    if(s.phase in listOf(AlienPhase.Hidden,AlienPhase.Defeated) || floor(s.point.other(axis)).toInt().mod(span)!=slice) return
    val shocked=shocks.active("E",0)
    val bitmap=imageResource(Res.drawable.alien_original_sheet)
    val frame=if(shocked) OutfitFrame(725,671,442,565) else when(s.phase) {
        AlienPhase.Attack -> OutfitFrame(643,44,495,577)
        AlienPhase.Leaving -> OutfitFrame(116,644,391,598)
        else -> OutfitFrame(206,15,197,606)
    }
    val painter=remember(bitmap,frame) { BitmapPainter(bitmap,IntOffset(frame.x,frame.y),IntSize(frame.width,frame.height),FilterQuality.None) }
    val left=centerX+unit*wrappedDelta(player.along(axis),s.point.along(axis),span)
    Image(painter,if(s.phase==AlienPhase.Warning) tr("Alien preparing to attack") else tr("Alien"),
        Modifier.align(Alignment.BottomStart).offset(x=left,y=-unit*s.point.row).size(unit*(if(s.phase==AlienPhase.Attack) 3 else 2),unit*3)
            .graphicsLayer { scaleX=if(s.facingRight) -1f else 1f },
        contentScale=ContentScale.Fit,alignment=Alignment.BottomCenter,
        alpha=if(s.phase==AlienPhase.Leaving && !reduceMotion) .6f else 1f,
        colorFilter=if(shocked) shockTint(shocks.clock,reduceMotion) else null)
    if(s.phase==AlienPhase.Warning && !shocked) Text("!",color=Color(0xFFFFCE66),fontSize=18.sp,
        modifier=Modifier.align(Alignment.BottomStart).offset(x=left+unit*.6f,y=-unit*(s.point.row+3)))
}
