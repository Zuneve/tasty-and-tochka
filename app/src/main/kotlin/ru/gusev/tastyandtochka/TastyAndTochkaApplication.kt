package ru.gusev.tastyandtochka

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TastyAndTochkaApplication

fun main(args: Array<String>) {
	runApplication<TastyAndTochkaApplication>(*args)
}
