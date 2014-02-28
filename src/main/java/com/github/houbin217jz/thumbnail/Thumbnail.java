/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.houbin217jz.thumbnail;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.Thumbnails.Builder;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

/**
 * 画像サイズ調整Sample
 * キーワード：　画像サイズ調整、Java 7 ディレクトリ階層を走査する
 * Jarファイルを画像フォルダにおいて、コマンドで実行する
 * 
 * 依存Library: 
 *   Thumbnailator: https://code.google.com/p/thumbnailator
 *   Common CLI: http://commons.apache.org/proper/commons-cli
 * @author Bin Hou
 *
 */
public class Thumbnail {

	public static void main(String[] args) {
		
		Options options = new Options(); 
		options.addOption("s", "src", true, "入力フォルダ、指定しないとデフォルトとして現在のフォルダが指定される");
		options.addOption("d", "dst", true, "出力フォルダ");
		options.addOption("r", "ratio", true, "拡大/縮小倍率, 30%の場合は0.3で指定してください");
		options.addOption("w", "width", true, "幅(px)");
		options.addOption("h", "height", true, "高さ(px)");
		options.addOption("R", "recursive", false, "再帰的に画像を探して変換する");
		
		HelpFormatter formatter = new HelpFormatter();
		String formatstr = "java -jar thumbnail.jar "
				+ "[-s/--src] 入力フォルダ "
				+ "[-d/--dst] 出力フォルダ "
				+ "[-r/--ratio] 倍率 "
				+ "[-w/--width] 幅 "
				+ "[-h/--height] 高さ "
				+ "[-R/--recursive] 再帰的";
		
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e1) {
			formatter.printHelp(formatstr, options);
			return;
		}
		
		final Path srcDir, dstDir;
		final Integer width, height;
		final Double ratio;
		
		//入力フォルダ
		if(cmd.hasOption("s")){
			srcDir = Paths.get(cmd.getOptionValue("s")).toAbsolutePath();
		} else {
			srcDir = Paths.get("").toAbsolutePath(); //現在のフォルダ
		}
		
		//出力フォルダ
		if(cmd.hasOption("d")){
			dstDir = Paths.get(cmd.getOptionValue("d")).toAbsolutePath();
		} else {
			formatter.printHelp(formatstr, options);
			return;
		}
		
		if(!Files.exists(srcDir, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(srcDir, LinkOption.NOFOLLOW_LINKS)){
			System.out.println("入力フォルダ["+srcDir.toAbsolutePath()+"]は存在していません。");
			return;
		}
		
		if(Files.exists(dstDir, LinkOption.NOFOLLOW_LINKS)){
			if(!Files.isDirectory(dstDir, LinkOption.NOFOLLOW_LINKS)){
				//指定されたパスはフォルダではない場合
				System.out.println("指定された出力フォルダはフォルダではありません。");
				return;
			}
		}else{
			//フォルダが存在しない場合、新規作成
			try {
				Files.createDirectories(dstDir);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
		
		//幅と高さ
		if(cmd.hasOption("w") && cmd.hasOption("h")){
			try {
				width = Integer.valueOf(cmd.getOptionValue("width"));
				height = Integer.valueOf(cmd.getOptionValue("height"));
			} catch (NumberFormatException e){
				System.out.println("幅と高さは不正です");
				return;
			}
		} else {
			width = null;
			height = null;
		}
		
		//倍率
		if(cmd.hasOption("r")){
			try {
				ratio = Double.valueOf(cmd.getOptionValue("r"));
			} catch (NumberFormatException e){
				System.out.println("倍率は不正です");
				return;
			}
		} else {
			ratio = null;
		}
		
		if(width != null && ratio !=null){
			System.out.println("幅・高さと倍率を同時に指定することができません");
			return;
		}
		
		if(width == null && ratio == null){
			System.out.println("幅・高さまたは倍率を指定してください");
			return;
		}
		
		//走査階層数
		int maxDepth = 1;
		if(cmd.hasOption("R")){
			maxDepth = Integer.MAX_VALUE;
		}
		
		try {
			//Java 7 ディレクトリ階層を走査する、@see http://docs.oracle.com/javase/jp/7/api/java/nio/file/Files.html
			Files.walkFileTree(srcDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), maxDepth, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path,
						BasicFileAttributes basicFileAttributes) throws IOException {

					//ファイル名を取得&小文字に変換
					String filename = path.getFileName().toString().toLowerCase();
					
					if(filename.endsWith(".jpg") || filename.endsWith(".jpeg") ){ 
						//Jpeg画像が発見
						
						/*
						 * relativeの意味:
						 * rootPath: /a/b/c/d
						 * filePath: /a/b/c/d/e/f.jpg
						 * rootPath.relativize(filePath) = e/f.jpg
						 */
						
						/*
						 * resolveの意味
						 * rootPath: /a/b/c/output
						 * relativePath: e/f.jpg
						 * rootPath.resolve(relativePath) = /a/b/c/output/e/f.jpg
						 */

						Path dst = dstDir.resolve(srcDir.relativize(path));
						
						if(!Files.exists(dst.getParent(), LinkOption.NOFOLLOW_LINKS)){
							Files.createDirectories(dst.getParent());
						}
						doResize(path.toFile(), dst.toFile(), width, height, ratio);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void doResize(File src, File dst, Integer width, Integer height, Double ratio) {
		try {
			Builder<File> builder = Thumbnails.of(src);
			if((width == null || height == null) && ratio != null){
				builder.scale(ratio).toFile(dst);
			}
			if((width !=null && height!=null) && ratio == null){
				builder.size(width, height).toFile(dst);
			}
			
		} catch (IOException e) {
			System.out.println("Error");
			System.out.println("src:"+src.toString());
			System.out.println("dst:"+dst.toString());
		}
	}
}
