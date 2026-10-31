package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

@Composable internal fun BoxScope.WorkbenchLayer(map: TileMap, player: RopePoint, axis: WorldAxis, unit: Dp, center: Dp) {
    val at = Crafting.bench(ExplorationWorld.biome(player))
    if (!map.exploration || !at.intersects(axis, map.activeSlice, map.width, 3) || !Crafting.benchSupported(map, at)) return
    Canvas(Modifier.align(Alignment.BottomStart)
        .offset(x = center + unit * wrappedDelta(player.along(axis), at.along(axis).toFloat(), map.width), y = -unit * at.row)
        .size(unit * 3.4f, unit * 2.2f).semantics { contentDescription = tr("Workbench") }) {
        drawWorkbench()
    }
}

/** The contextual action uses exactly the same artwork as the world station. */
@Composable internal fun WorkbenchIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawWorkbench() }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorkbench() {
        val w=size.width;val h=size.height
        val dark=Color(0xFF4F3024);val wood=Color(0xFFA86A3C);val light=Color(0xFFD5A46B)
        for(x in listOf(.12f,.76f)) {
            drawRoundRect(dark,Offset(w*x,h*.27f),Size(w*.15f,h*.73f),CornerRadius(w*.025f))
            drawRect(wood,Offset(w*(x+.025f),h*.34f),Size(w*.08f,h*.58f))
        }
        drawRoundRect(dark,Offset(0f,h*.18f),Size(w,h*.24f),CornerRadius(w*.04f))
        drawRoundRect(wood,Offset(w*.02f,h*.18f),Size(w*.96f,h*.16f),CornerRadius(w*.03f))
        drawLine(light,Offset(w*.06f,h*.21f),Offset(w*.92f,h*.21f),h*.03f,StrokeCap.Round)
        drawLine(dark,Offset(w*.17f,h*.78f),Offset(w*.83f,h*.78f),h*.09f,StrokeCap.Round)
        // A wooden mallet and offcut make the station recognisable at gameplay scale.
        drawLine(dark,Offset(w*.64f,h*.18f),Offset(w*.78f,h*.02f),w*.055f,StrokeCap.Round)
        drawRoundRect(light,Offset(w*.69f,0f),Size(w*.18f,h*.10f),CornerRadius(w*.02f))
        drawRoundRect(light,Offset(w*.19f,h*.10f),Size(w*.24f,h*.08f),CornerRadius(w*.02f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CraftingDialog(bag: PlayerInventory, pending: Boolean, canUse: Boolean, onMake: (String)->Unit, onClose: ()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),contentAlignment=Alignment.Center) {
            Surface(Modifier.widthIn(max=780.dp).fillMaxWidth().heightIn(max=maxHeight),shape=RoundedCornerShape(20.dp),color=Color(0xFF203C52),contentColor=Color.White) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(tr("Workbench"),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                        TextButton(onClick=onClose) { Text(tr("Done"),color=Color(0xFFFFCE66)) }
                    }
                    Text(tr("Choose a recipe. Use materials from your bag."))
                    if(pending) Text(tr("Waiting for the host…"))
                    if(!canUse) Text(tr("Move closer to the workbench"))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Crafting.recipes.forEach { recipe ->
                            val plan=Crafting.plan(bag,recipe.id)
                            Surface(Modifier.width(220.dp),shape=RoundedCornerShape(12.dp),color=Color(0xFF10243A),contentColor=Color.White) {
                                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                                    if(recipe.item==ItemType.Arrow) GameIcon(GameIconKind.Arrow,null,Modifier.size(42.dp))
                                    else EquipmentIcon(recipe.item,Modifier.size(42.dp))
                                    Text("${recipe.name} ×${recipe.count}",style=MaterialTheme.typography.titleMedium)
                                    recipe.materials.forEach { (material,needed) ->
                                        val have=Crafting.count(bag,material)
                                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                            BlockMaterialIcon(material,Modifier.size(24.dp))
                                            Text("${InventorySlot(ItemType.Block,1,material).displayName} $have/$needed",color=if(have>=needed)Color(0xFFACDF9D) else Color(0xFFFFC29D))
                                        }
                                    }
                                    Button(onClick={onMake(recipe.id)},enabled=canUse && !pending && plan.result==CraftResult.Crafted,modifier=Modifier.fillMaxWidth()) {
                                        Text(tr("Make {0}",recipe.name))
                                    }
                                    if(plan.result==CraftResult.BagFull) Text(tr("Inventory full"),fontSize=12.sp)
                                    else if(plan.result==CraftResult.MissingMaterials) Text(tr("Need more materials"),fontSize=12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
