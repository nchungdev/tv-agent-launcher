package com.gathin.tvhome.feature.agent.data

import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal object PairingPage {
    fun send(output: OutputStream, isAgent: Boolean, defaultUrl: String) {
        val title = if (isAgent) "Kết nối Duinch Agent" else "Kết nối Home Assistant"
        val tokenLabel = if (isAgent) "Access Token (Bearer)" else "Long-Lived Access Token"
        val hint = if (isAgent) {
            "Mở giao diện Web Home Sentinel -> tab Authen Token để tạo hoặc sao chép token."
        } else {
            "Mở Home Assistant -> Profile -> Security -> Tạo Long-Lived Access Token."
        }
        val accentColor = if (isAgent) "#9A82DB" else "#03A9F4"

        val html = "<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>$title</title><style>" +
            "*{box-sizing:border-box;margin:0;padding:0;font-family:-apple-system,sans-serif}" +
            "body{background:#121214;color:#fff;display:flex;justify-content:center;padding:24px 16px}" +
            ".card{background:#1E1E24;border-radius:20px;max-width:440px;width:100%;padding:24px;box-shadow:0 8px 30px rgba(0,0,0,.5)}" +
            "h1{font-size:20px;font-weight:700;margin-bottom:6px}" +
            "p.sub{font-size:13px;color:#a1a1aa;margin-bottom:20px}" +
            "label{display:block;font-size:13px;font-weight:600;color:#e4e4e7;margin-bottom:6px}" +
            "input{width:100%;height:46px;background:#27272e;border:1px solid #3f3f46;border-radius:12px;padding:0 14px;color:#fff;font-size:14px;margin-bottom:14px;outline:none}" +
            "input:focus{border-color:$accentColor}" +
            ".hint{font-size:12px;color:#71717a;margin-top:-8px;margin-bottom:18px;line-height:1.4}" +
            "button{width:100%;height:48px;background:$accentColor;border:none;border-radius:12px;color:#000;font-size:15px;font-weight:700;cursor:pointer}" +
            "button:disabled{opacity:.5}" +
            "#status{margin-top:16px;font-size:13px;text-align:center;line-height:1.4;display:none}" +
            ".success{color:#4ade80}" +
            ".error{color:#f87171}" +
            "</style></head><body><div class=\"card\"><h1>$title</h1>" +
            "<p class=\"sub\">Nhập thông tin bên dưới để kết nối trực tiếp tới TV</p>" +
            "<label>URL Máy chủ</label>" +
            "<input id=\"url\" type=\"url\" value=\"${android.text.TextUtils.htmlEncode(defaultUrl)}\" placeholder=\"http://192.168.1.x:8788\">" +
            "<label>$tokenLabel</label>" +
            "<input id=\"token\" type=\"password\" placeholder=\"Dán token vào đây\">" +
            "<div class=\"hint\">$hint</div>" +
            "<button id=\"btn\" onclick=\"save()\">Xác thực & Kết nối tới TV</button>" +
            "<div id=\"status\"></div></div><script>" +
            "async function save(){" +
            "const u=document.getElementById('url').value.trim()," +
            "t=document.getElementById('token').value.trim()," +
            "b=document.getElementById('btn')," +
            "s=document.getElementById('status');" +
            "if(!u||!t){s.className='error';s.style.display='block';s.innerText='Vui lòng nhập đầy đủ URL và Token';return;}" +
            "b.disabled=true;b.innerText='Đang xác thực…';s.style.display='none';" +
            "try{" +
            "const r=await fetch('/save',{method:'POST',headers:{'Content-Type':'application/json','X-Pairing-Code':location.hash.slice(1)},body:JSON.stringify({url:u,token:t})})," +
            "d=await r.json();" +
            "s.style.display='block';" +
            "if(d.ok){s.className='success';s.innerText=d.message+' Xác nhận trên TV để lưu.';b.innerText='Đã kết nối thành công';}" +
            "else{s.className='error';s.innerText='Lỗi: '+d.message;b.disabled=false;b.innerText='Thử lại';}" +
            "}catch(e){" +
            "s.className='error';s.style.display='block';s.innerText='Không kết nối được tới TV: '+e.message;b.disabled=false;b.innerText='Thử lại';" +
            "}" +
            "}</script></body></html>"

        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

}
