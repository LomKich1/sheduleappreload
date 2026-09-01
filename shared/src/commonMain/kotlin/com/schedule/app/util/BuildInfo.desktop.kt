package com.schedule.app.util

// На ПК нет отдельного debug/release флоу как на Android (packageMsi/packageDeb
// не различают debug и release так же строго) — поэтому пока всегда true.
// Если понадобится скрыть debug-инструменты и на десктопе тоже, сюда можно
// прокинуть системное свойство, которое CI будет выставлять только для
// тестовых сборок.
actual val IsDebugBuild: Boolean = true
