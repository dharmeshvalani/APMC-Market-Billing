package com.example.data

data class ShopConfig(
    val shopName: String = "APMC Trading Co.",
    val ownerName: String = "S. Valani",
    val gstNumber: String = "27AAAAA1111A1Z1",
    val apmcLicense: String = "APMC/MKT/2026/849",
    val address: String = "APMC Market Yard, Gate No. 2, Sector 19, Navi Mumbai, MH 400705",
    val logoUrl: String = "",
    val logoPreset: String = "GRAINS", // GRAINS, VEGETABLES, COTTON, FRUITS, STORE
    val invoicePrefix: String = "ATC/",
    val upiId: String = "svalani83@okaxis",
    val bankName: String = "State Bank of India",
    val bankAccName: String = "APMC Trading Co.",
    val bankAccNum: String = "12345678901",
    val bankIfsc: String = "SBIN0001234",
    val printerType: String = "Thermal 3-inch", // Thermal 2-inch, Thermal 3-inch, Standard A4
    val printAutomatically: Boolean = true,
    val theme: String = "System", // Light, Dark, System
    val language: String = "English", // English, Hindi, Marathi, Gujarati, Telugu
    val isCompleted: Boolean = false
)
