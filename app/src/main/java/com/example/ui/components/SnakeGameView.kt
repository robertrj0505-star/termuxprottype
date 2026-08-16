package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class Direction { UP, DOWN, LEFT, RIGHT }

data class Point(val x: Int, val y: Int)

@Composable
fun SnakeGameView(
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridSize = 16
    var snake by remember { mutableStateOf(listOf(Point(8, 8), Point(8, 9), Point(8, 10))) }
    var direction by remember { mutableStateOf(Direction.UP) }
    var food by remember { mutableStateOf(Point(5, 5)) }
    var score by remember { mutableIntStateOf(0) }
    var isGameOver by remember { mutableStateOf(false) }

    // Game loop
    LaunchedEffect(isGameOver) {
        while (!isGameOver) {
            delay(150)
            val head = snake.first()
            val newHead = when (direction) {
                Direction.UP -> Point(head.x, (head.y - 1 + gridSize) % gridSize)
                Direction.DOWN -> Point(head.x, (head.y + 1) % gridSize)
                Direction.LEFT -> Point((head.x - 1 + gridSize) % gridSize, head.y)
                Direction.RIGHT -> Point((head.x + 1) % gridSize, head.y)
            }

            if (snake.contains(newHead)) {
                isGameOver = true
            } else {
                val newSnake = mutableListOf(newHead)
                if (newHead == food) {
                    newSnake.addAll(snake)
                    score += 10
                    food = Point(Random.nextInt(gridSize), Random.nextInt(gridSize))
                } else {
                    newSnake.addAll(snake.dropLast(1))
                }
                snake = newSnake
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(12.dp)
            .testTag("snake_game_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TERMINAL SNAKE",
                color = Color(0xFF00FF66),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Score: $score",
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color(0xFF00FF66))
            ) {
                Text("Exit", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Game Board
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .background(Color(0xFF0A0F0D))
                .border(2.dp, Color(0xFF00FF66), RoundedCornerShape(4.dp))
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (y in 0 until gridSize) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (x in 0 until gridSize) {
                            val pt = Point(x, y)
                            val isHead = snake.firstOrNull() == pt
                            val isBody = snake.contains(pt) && !isHead
                            val isFood = food == pt

                            val cellColor = when {
                                isHead -> Color(0xFF00FF88)
                                isBody -> Color(0xFF0DBC79)
                                isFood -> Color(0xFFFF3366)
                                else -> Color.Transparent
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(cellColor)
                            )
                        }
                    }
                }
            }

            if (isGameOver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "GAME OVER",
                            color = Color(0xFFFF3366),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Final Score: $score",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Button(
                            onClick = {
                                snake = listOf(Point(8, 8), Point(8, 9), Point(8, 10))
                                direction = Direction.UP
                                food = Point(5, 5)
                                score = 0
                                isGameOver = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66), contentColor = Color.Black)
                        ) {
                            Text("Restart", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // On-Screen D-Pad
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            IconButton(
                onClick = { if (direction != Direction.DOWN) direction = Direction.UP },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1E262B), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Color(0xFF00FF66))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                IconButton(
                    onClick = { if (direction != Direction.RIGHT) direction = Direction.LEFT },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF1E262B), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Left", tint = Color(0xFF00FF66))
                }
                IconButton(
                    onClick = { if (direction != Direction.LEFT) direction = Direction.RIGHT },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF1E262B), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Right", tint = Color(0xFF00FF66))
                }
            }
            IconButton(
                onClick = { if (direction != Direction.UP) direction = Direction.DOWN },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1E262B), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = Color(0xFF00FF66))
            }
        }
    }
}
