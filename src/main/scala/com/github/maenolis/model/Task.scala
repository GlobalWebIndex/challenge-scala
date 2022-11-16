package com.github.maenolis.model

final case class Task(id: Long, uri: String, status: TaskStatus.Value)