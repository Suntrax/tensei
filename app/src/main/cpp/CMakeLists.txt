cmake_minimum_required(VERSION 3.22.1)
project("tensei_discord")

find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(tensei_discord SHARED
    discord_jni.cpp
)

target_link_libraries(tensei_discord
    discord_partner_sdk::discord_partner_sdk
    android
    log
)
