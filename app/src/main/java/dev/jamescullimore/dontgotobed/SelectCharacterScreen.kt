package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun SelectCharacterScreen(localIp: String, mp: MultiplayerManager?, setMp: (MultiplayerManager?) -> Unit, onSelect: (Player, Boolean, String?) -> Unit) {
    var roleHost by remember { mutableStateOf<Boolean?>(null) }
    var joinIp by remember { mutableStateOf("") }

    var testing by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var peerSelected by remember { mutableStateOf<String?>(null) }

    fun attachUiListener(manager: MultiplayerManager?) {
        manager?.updateListener(object : MultiplayerManager.Listener {
            override fun onConnectionChanged(connected: Boolean) {
                isConnected = connected
                if (connected) testing = false
            }
            override fun onPeerSelectedPlayer(id: Int, name: String) {
                peerSelected = name
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Choose your character", color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Multiplayer", color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (roleHost != true) {
                        roleHost = true
                        testing = true
                        val mgr = MultiplayerManager(
                            isHost = true,
                            hostIp = MultiplayerManager.DEFAULT_HOST_IP,
                            port = MultiplayerManager.DEFAULT_PORT,
                            initialListener = object : MultiplayerManager.Listener {
                                override fun onConnectionChanged(connected: Boolean) {
                                    isConnected = connected
                                    if (connected) testing = false
                                }
                            }
                        )
                        setMp(mgr)
                        mgr.start()
                    } else {
                        attachUiListener(mp)
                    }
                }) { Text("Host") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    if (roleHost != false) {
                        roleHost = false
                        testing = false
                        isConnected = false
                    }
                }) { Text("Join") }
            }
            Spacer(Modifier.height(6.dp))
            if (roleHost == true) {
                Text(text = "Your IP: $localIp  Port: ${MultiplayerManager.DEFAULT_PORT}", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    attachUiListener(mp)
                }) { Text(if (!isConnected && !testing) "Waiting for client..." else if (testing) "Waiting for client..." else "Connected!") }
            } else if (roleHost == false) {
                TextField(
                    value = joinIp,
                    onValueChange = { joinIp = it },
                    label = { Text("Enter Host IP") }
                )
                Spacer(Modifier.height(4.dp))
                Text(text = "Port: ${MultiplayerManager.DEFAULT_PORT} (fixed)", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        testing = true
                        val ip = joinIp.ifBlank { MultiplayerManager.DEFAULT_HOST_IP }
                        val mgr = MultiplayerManager(
                            isHost = false,
                            hostIp = ip,
                            port = MultiplayerManager.DEFAULT_PORT,
                            initialListener = object : MultiplayerManager.Listener {
                                override fun onConnectionChanged(connected: Boolean) {
                                    isConnected = connected
                                    testing = false
                                }
                            }
                        )
                        setMp(mgr)
                        mgr.start()
                    },
                    enabled = joinIp.isNotBlank() && !isConnected && !testing
                ) { Text(if (!isConnected && !testing) "Test Connection" else if (testing) "Connecting..." else "Connected!") }
            }

            if (isConnected) {
                Spacer(Modifier.height(6.dp))
                Text(text = "Connected! You can select your character.", color = Color.Green)
                if (peerSelected != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = "Peer selected: $peerSelected", color = Color.Yellow)
                }
            }

            Spacer(Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        mp?.sendSelectedPlayer(Player.Leo.name)
                        onSelect(Player.Leo, roleHost ?: true, if (roleHost == false) joinIp else null)
                    },
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.leo_front),
                        contentDescription = "Leo",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Leo")
                }

                Button(
                    onClick = {
                        mp?.sendSelectedPlayer(Player.Ian.name)
                        onSelect(Player.Ian, roleHost ?: true, if (roleHost == false) joinIp else null)
                    },
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ian_front),
                        contentDescription = "Ian",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ian")
                }

                Button(
                    onClick = {
                        mp?.sendSelectedPlayer(Player.Papa.name)
                        onSelect(Player.Papa, roleHost ?: true, if (roleHost == false) joinIp else null)
                    },
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.papa_front),
                        contentDescription = "Papa",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Papa")
                }
            }
        }
    }
}