package com.devanand.tapmaanapp.models

import java.io.Serializable

data class WeatherResponse(
    val coord:Coordinate,
    val weather: List<Weather>,
    val base:String,
    val visibilty:Int,
    val wind:Wind,
    val clouds:Clouds,
    val dt:Int,
    val sys:Sys,
    val id:Int,
    val name:String,
    val cod:Int,
    val main:Main
): Serializable
